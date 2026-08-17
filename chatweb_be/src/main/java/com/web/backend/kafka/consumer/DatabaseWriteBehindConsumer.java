package com.web.backend.kafka.consumer;

import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.service.WebSocketRoutingService;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.mapper.MessageMapper;

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

    private static final String QUEUE_MESSAGES_STRING = "/queue/messages";

    @RetryableTopic(kafkaTemplate = "avroChatKafkaTemplate", attempts = "Integer.MAX_VALUE", backoff = @Backoff(delay = 5000, multiplier = 2.0, maxDelay = 300000, random = true), autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-save-group-id}", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDbPersistence(List<ChatMessageAvro> messagePayloads) {
        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();
        if (payloadsToSave.isEmpty()) {
            return;
        }
        payloadsToSave.forEach(msg -> msg.setStatus(MessageStatus.SENT.name()));
        log.debug("Persisting write-behind batch of {} chat messages (Avro) to MongoDB...", payloadsToSave.size());

        List<ChatMessage> entitiesToSave = payloadsToSave.stream()
                .map(messageMapper::toEntity)
                .toList();

        try {
            mongoTemplate.insertAll(entitiesToSave);
            log.info("Persisted batch of {} chat messages to MongoDB successfully", entitiesToSave.size());
        } catch (DuplicateKeyException e) {
            log.warn("Batch contains messages already persisted to MongoDB, skipping duplicates: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to persist batch of {} chat messages to MongoDB. Triggering Kafka retry...",
                    payloadsToSave.size(), e);
            throw e;
        }
        sendAcknowledgements(payloadsToSave);
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
