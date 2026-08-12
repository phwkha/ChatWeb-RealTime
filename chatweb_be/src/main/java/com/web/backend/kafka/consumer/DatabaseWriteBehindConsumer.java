package com.web.backend.kafka.consumer;

import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageType;
import com.web.backend.model.ChatMessage;
import com.web.backend.repository.MessageRepository;
import com.web.backend.service.util.WebSocketRoutingService;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.form.SocketResponse;
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

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.save}", containerFactory = "batchFactory")
    public void handleDbPersistence(List<ChatMessage> messages) {
        List<ChatMessage> messagesToSave = messages.stream().filter(msg -> msg.getMessageType() == MessageType.CHAT)
                .toList();
        if (messagesToSave.isEmpty()) {
            return;
        }
        log.info("Kafka Consumer: Writing batch of {} messages to Database...", messages.size());
        int maxAttempts = 3;
        long backoffDelay = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                messageRepository.saveAll(messagesToSave);
                log.info("Successfully saved {} messages.", messages.size());

                sendAcknowledgements(messagesToSave);

                return;
            } catch (Exception e) {
                log.warn("Error writing to DB (Attempt {}/{}): {}", attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Database save FAILED after {} attempts. Throwing exception to prevent data loss.",
                            maxAttempts);
                    throw new RuntimeException("Failed to save messages to DB after " + maxAttempts + " attempts", e);
                } else {
                    try {
                        Thread.sleep(backoffDelay);
                        backoffDelay *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread interrupted during DB retry backoff", ie);
                    }
                }
            }
        }
    }

    private void sendAcknowledgements(List<ChatMessage> messagesToSave) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ChatMessage msg : messagesToSave) {
                executor.submit(() -> {
                    try {
                        ChatMessageResponse messageResponse = messageMapper.toResponse(msg);
                        messageResponse.setLocalId(msg.getLocalId());
                        SocketResponse<ChatMessageResponse> response = SocketResponse.message(messageResponse);
                        webSocketRoutingService.routeMessage(msg.getSender(), QUEUE_MESSAGES_STRING, response);
                    } catch (Exception ex) {
                        log.error("Error sending ACK to sender: {}", ex.getMessage());
                    }
                });
            }
        }
    }
}
