package com.web.backend.kafka.consumer;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.mapper.MessageMapper;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.dao.DuplicateKeyException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "DATABASE-DLT-CONSUMER")
public class DatabaseDLTConsumer {

    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";
    private static final String CHAT_RECENT_ZSET_STRING = "chat:recent:zset:";

    @KafkaListener(topics = "chat.messages.dlt", groupId = "${spring.kafka.topic.chat.messages-save-group-id}.dlt", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDltPersistence(List<ChatMessageAvro> messagePayloads) {
        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();
        
        if (payloadsToSave.isEmpty()) return;

        // 1. Kiểm tra và Nạp Redis (30-31 phút)
        for (ChatMessageAvro msg : payloadsToSave) {
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
                            long score = entity.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            redisTemplate.opsForZSet().add(zsetKey, entity.getId(), score);
                        }
                    }
                    
                    long jitter = ThreadLocalRandom.current().nextLong(0, 61);
                    Duration ttl = Duration.ofSeconds(1800 + jitter);
                    redisTemplate.expire(hashKey, ttl);
                    redisTemplate.expire(zsetKey, ttl);
                }
            } catch (Exception ex) {
                log.warn("Failed to update Redis cache for DLT message '{}'", msg.getId(), ex);
            }
        }

        // 2. Thử lưu vào MongoDB
        payloadsToSave.forEach(msg -> msg.setStatus(MessageStatus.SENT.name()));
        List<ChatMessage> entitiesToSave = payloadsToSave.stream().map(messageMapper::toEntity).toList();

        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            bulkOps.insert(entitiesToSave);
            bulkOps.execute();
            log.info("DLT: Persisted batch of {} chat messages to MongoDB successfully", entitiesToSave.size());
        } catch (BulkOperationException e) {
            log.warn("DLT: Batch bulk insert encountered duplicate keys or partial failure, valid items still persisted: {}", e.getMessage());
        } catch (DuplicateKeyException e) {
            log.warn("DLT: Batch contains messages already persisted to MongoDB, skipping.");
        } catch (Exception e) {
            log.error("DLT: Failed to persist batch of {} chat messages. Retrying in 5 seconds...", payloadsToSave.size());
            throw e; 
        }
    }
}
