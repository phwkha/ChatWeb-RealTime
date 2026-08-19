package com.web.backend.kafka.consumer;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.mapper.MessageMapper;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.dao.DuplicateKeyException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "DATABASE-WRITE-BEHIND-CONSUMER")
public class DatabaseWriteBehindConsumer {

    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, ChatMessageAvro> avroChatKafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";
    private static final String CHAT_RECENT_ZSET_STRING = "chat:recent:zset:";
    private static final String DLT_TOPIC = "chat.messages.dlt";
    private static final long MAX_RETRY_CYCLE_MS = 290_000L;

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-save-group-id}", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDbPersistence(List<ChatMessageAvro> messagePayloads) {
        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();
        if (payloadsToSave.isEmpty()) {
            return;
        }

        log.debug("Persisting write-behind batch of {} chat messages (Avro) to MongoDB...", payloadsToSave.size());

        List<ChatMessage> entitiesToSave = payloadsToSave.stream()
                .map(messageMapper::toEntity)
                .toList();

        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            bulkOps.insert(entitiesToSave);
            bulkOps.execute();
            log.info("Persisted batch of {} chat messages to MongoDB successfully", entitiesToSave.size());
        } catch (DuplicateKeyException | BulkOperationException e) {
            log.warn("Batch contains messages already persisted to MongoDB, skipping duplicates: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to persist batch of {} chat messages to MongoDB. Routing to DLT synchronously...",
                    payloadsToSave.size(), e);
            try {
                List<CompletableFuture<?>> futures = new ArrayList<>();
                for (ChatMessageAvro msg : payloadsToSave) {
                    futures.add(avroChatKafkaTemplate.send(DLT_TOPIC, msg.getConversationId(), msg));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
                log.info("Successfully routed batch of {} chat messages to DLT", payloadsToSave.size());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Thread was interrupted while waiting for DLT routing", ie);
                throw new RuntimeException("DLT routing interrupted", ie);
            } catch (Exception kafkaEx) {
                log.error("CRITICAL: Failed to route messages to DLT. Rethrowing exception to abort offset commit and prevent data loss!", kafkaEx);
                throw new RuntimeException("Failed to persist to MongoDB and DLT routing failed", kafkaEx);
            }
        }
    }

    @KafkaListener(topics = DLT_TOPIC, groupId = "${spring.kafka.topic.chat.messages-save-group-id}.dlt.cache-group", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDltCacheExtension(List<ChatMessageAvro> messagePayloads) {
        List<ChatMessageAvro> payloadsToCache = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();

        if (payloadsToCache.isEmpty())
            return;

        for (ChatMessageAvro msg : payloadsToCache) {
            try {
                String convId = msg.getConversationId();
                if (convId != null) {
                    String hashKey = CHAT_RECENT_HASH_STRING + convId;
                    String zsetKey = CHAT_RECENT_ZSET_STRING + convId;

                    Boolean hasKey = redisTemplate.opsForHash().hasKey(hashKey, msg.getId());
                    if (Boolean.FALSE.equals(hasKey)) {
                        ChatMessage entity = messageMapper.toEntity(msg);
                        redisTemplate.opsForHash().put(hashKey, entity.getId(), entity);
                        if (entity.getTimestamp() != null) {
                            long score = entity.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()
                                    .toEpochMilli();
                            redisTemplate.opsForZSet().add(zsetKey, entity.getId(), score);
                        }
                    }

                    long jitter = ThreadLocalRandom.current().nextLong(0, 31);
                    Duration ttl = Duration.ofSeconds(300 + jitter);
                    redisTemplate.expire(hashKey, ttl);
                    redisTemplate.expire(zsetKey, ttl);
                }
            } catch (Exception ex) {
                log.warn("DLT Cache: Failed to update Redis cache for message '{}'", msg.getId(), ex);
            }
        }
        log.info("DLT Cache: Extended Redis TTL for {} messages", payloadsToCache.size());
    }

    @KafkaListener(topics = DLT_TOPIC, groupId = "${spring.kafka.topic.chat.messages-save-group-id}.dlt.retry-group", containerFactory = "dltRetryBatchListenerContainerFactory")
    public void handleDltDbRetry(List<ChatMessageAvro> messagePayloads,
            @Header(name = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) List<Long> timestamps) {
        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();

        if (payloadsToSave.isEmpty())
            return;

        long oldestKafkaTimestamp = (timestamps != null && !timestamps.isEmpty())
                ? timestamps.stream().filter(Objects::nonNull).min(Long::compareTo).orElse(System.currentTimeMillis())
                : System.currentTimeMillis();
        long ageMillis = System.currentTimeMillis() - oldestKafkaTimestamp;

        if (ageMillis >= MAX_RETRY_CYCLE_MS) {
            log.warn("DLT Retry: Batch age {} ms reached cycle limit. Republishing to DLT to renew Redis TTL...",
                    ageMillis);
            try {
                List<CompletableFuture<?>> futures = new ArrayList<>();
                for (ChatMessageAvro msg : payloadsToSave) {
                    futures.add(avroChatKafkaTemplate.send(DLT_TOPIC, msg.getConversationId(), msg));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Thread was interrupted while waiting for DLT republish", ie);
                throw new RuntimeException("DLT republish interrupted", ie);
            } catch (Exception kafkaEx) {
                log.error("CRITICAL: Failed to republish batch to DLT. Rethrowing exception to prevent offset commit!", kafkaEx);
                throw new RuntimeException("DLT republish failed", kafkaEx);
            }
            return;
        }

        List<ChatMessage> entitiesToSave = payloadsToSave.stream().map(messageMapper::toEntity).toList();

        try {
            List<String> idsToSave = entitiesToSave.stream().map(ChatMessage::getId).toList();
            Query query = new Query(Criteria.where("_id").in(idsToSave));
            query.fields().include("_id");
            List<ChatMessage> existingDocs = mongoTemplate.find(query, ChatMessage.class);
            Set<String> existingIds = existingDocs.stream().map(ChatMessage::getId).collect(Collectors.toSet());

            List<ChatMessage> newEntities = entitiesToSave.stream()
                    .filter(msg -> !existingIds.contains(msg.getId()))
                    .toList();

            if (newEntities.isEmpty()) {
                return;
            }

            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            bulkOps.insert(newEntities);
            bulkOps.execute();
            log.info("DLT Retry: Persisted batch of {} new chat messages to MongoDB successfully after DB recovery",
                    newEntities.size());
        } catch (DuplicateKeyException | BulkOperationException e) {
            log.warn("DLT Retry: Batch contains messages already persisted to MongoDB, skipping duplicates: {}", e.getMessage());
        } catch (Exception e) {
            log.error(
                    "DLT Retry: Failed to persist batch of {} chat messages. Retrying via ErrorHandler (backoff)...",
                    payloadsToSave.size(), e);
            throw e;
        }
    }
}
