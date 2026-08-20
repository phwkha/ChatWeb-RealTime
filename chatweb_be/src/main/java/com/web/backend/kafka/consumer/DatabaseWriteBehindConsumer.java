package com.web.backend.kafka.consumer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.mongodb.bulk.BulkWriteError;
import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.service.WebSocketRoutingService;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.ErrorSocketResponse;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.mapper.MessageMapper;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final WebSocketRoutingService webSocketRoutingService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEUE_MESSAGES_STRING = "/queue/messages";
    private static final String QUEUE_ERRORS_STRING = "/queue/errors";
    private static final String ERROR_SYS_PROCESSING_MSG_STRING = "error.sys.processing_msg";

    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";
    private static final String CHAT_RECENT_ZSET_STRING = "chat:recent:zset:";
    private static final String UNREAD_COUNTS_STRING = "unread_counts:";

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-save-group-id}", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDbPersistence(List<ChatMessageAvro> messagePayloads) {
        if (messagePayloads == null || messagePayloads.isEmpty()) {
            return;
        }

        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();
        if (payloadsToSave.isEmpty()) {
            return;
        }

        List<ChatMessage> entitiesToSave = payloadsToSave.stream()
                .map(avro -> {
                    ChatMessage entity = messageMapper.toEntity(avro);
                    entity.setStatus(MessageStatus.SENT);
                    return entity;
                })
                .toList();

        List<ChatMessageAvro> successfullySaved = new ArrayList<>();
        List<ChatMessageAvro> failedPayloads = new ArrayList<>();

        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            bulkOps.insert(entitiesToSave);
            bulkOps.execute();
            successfullySaved.addAll(payloadsToSave);
            log.debug("Persisted batch of {} chat messages to MongoDB", entitiesToSave.size());
        } catch (DuplicateKeyException dke) {
            log.warn("Duplicate key detected in batch, treating as idempotent save: {}", dke.getMessage());
            successfullySaved.addAll(payloadsToSave);
        } catch (BulkOperationException boe) {
            log.warn("Bulk operation exception occurred during message batch persistence: {}", boe.getMessage());
            classifyBulkOperationResults(payloadsToSave, boe, successfullySaved, failedPayloads);
        } catch (Exception ex) {
            log.error("Fatal exception during batch database persistence of {} messages. Delegating to Kafka retry.",
                    entitiesToSave.size(), ex);
            throw ex;
        }

        if (!successfullySaved.isEmpty()) {
            sendAcknowledgements(successfullySaved);
        }
        if (!failedPayloads.isEmpty()) {
            sendErrorMessage(failedPayloads);
        }
    }

    @KafkaListener(topics = "chat.messages.dlt", groupId = "${spring.kafka.topic.chat.messages-save-group-id}-dlt", containerFactory = "chatAvroListenerContainerFactory")
    public void handleDltPersistence(ChatMessageAvro message) {
        if (message == null || !MessageType.CHAT.name().equalsIgnoreCase(message.getMessageType())) {
            return;
        }

        try {
            ChatMessage entity = messageMapper.toEntity(message);
            entity.setStatus(MessageStatus.SENT);
            mongoTemplate.save(entity);
            log.info("Successfully recovered and saved message '{}' from DLT to MongoDB", message.getId());

            evictMessageCacheOnSuccess(message);

            ChatMessageResponse response = messageMapper.avroToResponse(message);
            response.setStatus(MessageStatus.SENT);
            webSocketRoutingService.routeMessage(message.getSender(), QUEUE_MESSAGES_STRING, response);
        } catch (DuplicateKeyException dke) {
            log.warn("Message '{}' in DLT was already saved (idempotent)", message.getId());
            evictMessageCacheOnSuccess(message);
            try {
                ChatMessageResponse response = messageMapper.avroToResponse(message);
                response.setStatus(MessageStatus.SENT);
                webSocketRoutingService.routeMessage(message.getSender(), QUEUE_MESSAGES_STRING, response);
            } catch (Exception ex) {
                log.error("Failed to route ACK from DLT for message '{}'", message.getId(), ex);
            }
        } catch (Exception ex) {
            log.error(
                    "Permanently failed to persist message '{}' from DLT. Dispatching error notification to user '{}'",
                    message.getId(), message.getSender(), ex);
            evictMessageCacheOnFailure(message);
            try {
                ChatMessageResponse response = messageMapper.avroToResponse(message);
                String errorMsg = Translator.tolocale(ERROR_SYS_PROCESSING_MSG_STRING);
                webSocketRoutingService.routeMessage(message.getSender(), QUEUE_ERRORS_STRING,
                        ErrorSocketResponse.builder()
                                .message(errorMsg)
                                .request(response)
                                .build());
            } catch (Exception wsEx) {
                log.error("Failed to route error notification from DLT to user '{}'", message.getSender(), wsEx);
            }
        }
    }

    private void classifyBulkOperationResults(
            List<ChatMessageAvro> payloadsToSave,
            BulkOperationException boe,
            List<ChatMessageAvro> successfullySaved,
            List<ChatMessageAvro> failedPayloads) {

        Set<Integer> failedIndices = new HashSet<>();
        if (boe.getErrors() != null) {
            for (BulkWriteError error : boe.getErrors()) {
                if (error.getCode() != 11000) {
                    failedIndices.add(error.getIndex());
                    log.error("Bulk write error at index {}: code={}, message={}",
                            error.getIndex(), error.getCode(), error.getMessage());
                } else {
                    log.debug("Duplicate key at index {} ignored (idempotent write)", error.getIndex());
                }
            }
        }

        for (int i = 0; i < payloadsToSave.size(); i++) {
            if (failedIndices.contains(i)) {
                failedPayloads.add(payloadsToSave.get(i));
            } else {
                successfullySaved.add(payloadsToSave.get(i));
            }
        }
    }

    private void sendAcknowledgements(List<ChatMessageAvro> payloadsToSave) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ChatMessageAvro payload : payloadsToSave) {
                executor.submit(() -> {
                    evictMessageCacheOnSuccess(payload);
                    try {
                        ChatMessageResponse messageResponse = messageMapper.avroToResponse(payload);
                        messageResponse.setStatus(MessageStatus.SENT);
                        webSocketRoutingService.routeMessage(payload.getSender(), QUEUE_MESSAGES_STRING,
                                messageResponse);
                        log.debug("Dispatched persistence ACK to sender '{}' for message '{}' (localId='{}')",
                                payload.getSender(), payload.getId(), payload.getLocalId());
                    } catch (Exception ex) {
                        log.error("Failed to route persistence ACK to sender '{}' for message '{}'",
                                payload.getSender(), payload.getId(), ex);
                    }
                });
            }
        }
    }

    private void sendErrorMessage(List<ChatMessageAvro> payloadsError) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ChatMessageAvro payload : payloadsError) {
                executor.submit(() -> {
                    evictMessageCacheOnFailure(payload);
                    try {
                        ChatMessageResponse messageResponse = messageMapper.avroToResponse(payload);
                        String errorMsg = Translator.tolocale(ERROR_SYS_PROCESSING_MSG_STRING);
                        webSocketRoutingService.routeMessage(payload.getSender(), QUEUE_ERRORS_STRING,
                                ErrorSocketResponse.builder()
                                        .message(errorMsg)
                                        .request(messageResponse)
                                        .build());
                        log.warn("Dispatched persistence ERROR to sender '{}' for message '{}' (localId='{}')",
                                payload.getSender(), payload.getId(), payload.getLocalId());
                    } catch (Exception ex) {
                        log.error("Failed to route persistence ERROR to sender '{}' for message '{}'",
                                payload.getSender(), payload.getId(), ex);
                    }
                });
            }
        }
    }

    private void evictMessageCacheOnSuccess(ChatMessageAvro payload) {
        if (payload == null || payload.getConversationId() == null || payload.getId() == null) {
            return;
        }
        try {
            String convId = payload.getConversationId();
            String hashKey = CHAT_RECENT_HASH_STRING + convId;
            String zsetKey = CHAT_RECENT_ZSET_STRING + convId;
            redisTemplate.opsForZSet().remove(zsetKey, payload.getId());
            redisTemplate.opsForHash().delete(hashKey, payload.getId());
            log.debug("Evicted message '{}' from Redis cache on persistence success", payload.getId());
        } catch (Exception ex) {
            log.warn("Failed to evict Redis cache for message '{}' on success", payload.getId(), ex);
        }
    }

    private void evictMessageCacheOnFailure(ChatMessageAvro payload) {
        if (payload == null || payload.getConversationId() == null || payload.getId() == null) {
            return;
        }
        try {
            String convId = payload.getConversationId();
            String hashKey = CHAT_RECENT_HASH_STRING + convId;
            String zsetKey = CHAT_RECENT_ZSET_STRING + convId;
            redisTemplate.opsForZSet().remove(zsetKey, payload.getId());
            redisTemplate.opsForHash().delete(hashKey, payload.getId());

            if (payload.getRecipient() != null && payload.getSender() != null) {
                String unreadKey = UNREAD_COUNTS_STRING + payload.getRecipient();
                Long count = redisTemplate.opsForHash().increment(unreadKey, payload.getSender(), -1);
                if (count != null && count <= 0) {
                    redisTemplate.opsForHash().delete(unreadKey, payload.getSender());
                }
            }
            log.debug("Evicted message '{}' and rolled back unread count on persistence failure", payload.getId());
        } catch (Exception ex) {
            log.warn("Failed to evict Redis cache for message '{}' on failure", payload.getId(), ex);
        }
    }
}
