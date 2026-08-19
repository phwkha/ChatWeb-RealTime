package com.web.backend.kafka.consumer;

import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.service.WebSocketRoutingService;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.mapper.MessageMapper;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final KafkaTemplate<String, ChatMessageAvro> avroChatKafkaTemplate;

    private static final String QUEUE_MESSAGES_STRING = "/queue/messages";
    private static final long MAX_RETRY_DURATION_MS = 240_000L;
    private static final String DLT_TOPIC = "chat.messages.dlt";

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-save-group-id}", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDbPersistence(List<ChatMessageAvro> messagePayloads,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) List<Long> timestamps) {
        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();
        if (payloadsToSave.isEmpty()) {
            return;
        }

        if (isBatchExpiredAndRoutedToDlt(timestamps, payloadsToSave)) {
            return;
        }

        payloadsToSave.forEach(msg -> msg.setStatus(MessageStatus.SENT.name()));
        log.debug("Persisting write-behind batch of {} chat messages (Avro) to MongoDB...", payloadsToSave.size());

        List<ChatMessage> entitiesToSave = payloadsToSave.stream()
                .map(messageMapper::toEntity)
                .toList();

        saveBatchToMongo(entitiesToSave, payloadsToSave.size());
        sendAcknowledgements(payloadsToSave);
    }

    private boolean isBatchExpiredAndRoutedToDlt(List<Long> timestamps, List<ChatMessageAvro> payloadsToSave) {
        long oldestKafkaTimestamp = timestamps.stream().min(Long::compareTo).orElse(System.currentTimeMillis());
        long ageMillis = System.currentTimeMillis() - oldestKafkaTimestamp;

        if (ageMillis >= MAX_RETRY_DURATION_MS) {
            log.warn("Batch is older than 4 minutes (age: {} ms). Routing to DLT topic '{}'", ageMillis, DLT_TOPIC);
            for (ChatMessageAvro msg : payloadsToSave) {
                avroChatKafkaTemplate.send(DLT_TOPIC, msg.getConversationId(), msg);
            }
            return true;
        }
        return false;
    }

    private void saveBatchToMongo(List<ChatMessage> entitiesToSave, int batchSize) {
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            bulkOps.insert(entitiesToSave);
            bulkOps.execute();
            log.info("Persisted batch of {} chat messages to MongoDB successfully", entitiesToSave.size());
        } catch (BulkOperationException e) {
            log.warn("Batch bulk insert encountered duplicate keys or partial failure, valid items still persisted: {}", e.getMessage());
        } catch (DuplicateKeyException e) {
            log.warn("Batch contains messages already persisted to MongoDB, skipping duplicates: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to persist batch of {} chat messages to MongoDB. Triggering Kafka retry...",
                    batchSize, e);
            throw e;
        }
    }

    private void sendAcknowledgements(List<ChatMessageAvro> payloadsToSave) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ChatMessageAvro payload : payloadsToSave) {
                executor.submit(() -> {
                    try {
                        ChatMessageResponse messageResponse = messageMapper.avroToResponse(payload);
                        webSocketRoutingService.routeMessage(payload.getSender(), QUEUE_MESSAGES_STRING,
                                messageResponse);
                        log.debug("Dispatched persistence ACK to sender '{}' for message '{}'", payload.getSender(),
                                payload.getId());
                    } catch (Exception ex) {
                        log.error("Failed to route persistence ACK to sender '{}' for message '{}'",
                                payload.getSender(),
                                payload.getId(), ex);
                    }
                });
            }
        }
    }
}
