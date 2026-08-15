package com.web.backend.kafka.consumer;

import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageType;
import com.web.backend.kafka.payload.ChatMessagePayload;
import com.web.backend.model.ChatMessage;
import com.web.backend.repository.MessageRepository;
import com.web.backend.service.WebSocketRoutingService;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.mapper.MessageMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "DATABASE-WRITE-BEHIND-CONSUMER")
public class DatabaseWriteBehindConsumer {

    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final WebSocketRoutingService webSocketRoutingService;

    private static final String QUEUE_MESSAGES_STRING = "/queue/messages";

    @RetryableTopic(attempts = "Integer.MAX_VALUE", backoff = @Backoff(delay = 300000, maxDelay = 300000), autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-save-group-id}", containerFactory = "batchFactory")
    public void handleDbPersistence(List<ChatMessagePayload> messagePayloads) {
        List<ChatMessagePayload> payloadsToSave = messagePayloads.stream()
                .filter(msg -> msg.getMessageType() == MessageType.CHAT)
                .toList();
        if (payloadsToSave.isEmpty()) {
            return;
        }
        log.debug("Persisting write-behind batch of {} chat messages to MongoDB...", payloadsToSave.size());
        
        List<ChatMessage> entitiesToSave = payloadsToSave.stream()
                .map(messageMapper::toEntity)
                .toList();

        try {
            messageRepository.saveAll(entitiesToSave);
            log.info("Persisted batch of {} chat messages to MongoDB successfully", entitiesToSave.size());
        } catch (Exception e) {
            log.error("Failed to persist batch of {} chat messages to MongoDB. Triggering Kafka retry...",
                    payloadsToSave.size(), e);
            throw e;
        }
        sendAcknowledgements(payloadsToSave);
    }

    private void sendAcknowledgements(List<ChatMessagePayload> payloadsToSave) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ChatMessagePayload payload : payloadsToSave) {
                executor.submit(() -> {
                    try {
                        ChatMessageResponse messageResponse = messageMapper.payloadToResponse(payload);
                        webSocketRoutingService.routeMessage(payload.getSender(), QUEUE_MESSAGES_STRING, messageResponse);
                        log.debug("Dispatched persistence ACK to sender '{}' for message '{}'", payload.getSender(),
                                payload.getId());
                    } catch (Exception ex) {
                        log.error("Failed to route persistence ACK to sender '{}' for message '{}'", payload.getSender(),
                                payload.getId(), ex);
                    }
                });
            }
        }
    }
}
