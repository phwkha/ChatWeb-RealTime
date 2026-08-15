package com.web.backend.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.web.backend.kafka.payload.ChatMessagePayload;

@Component
@Slf4j(topic = "CHAT-KAFKA-PRODUCER")
@RequiredArgsConstructor
public class ChatProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic.chat.messages}")
    private String chatTopic;

    @Value("${spring.kafka.topic.chat.system-messages}")
    private String systemTopic;

    @Value("${spring.kafka.topic.update-message.update}")
    private String chatTopicUpdate;

    public CompletableFuture<SendResult<String, Object>> sendChatMessage(ChatMessagePayload messageChat) {
        return sendSafely(chatTopic, messageChat, "Chat Message");
    }

    public CompletableFuture<SendResult<String, Object>> sendSystemMessage(Object messageSystem) {
        return sendSafely(systemTopic, messageSystem, "System Message");
    }

    public CompletableFuture<SendResult<String, Object>> sendReaction(Object messageReaction) {
        return sendSafely(chatTopicUpdate, messageReaction, "Reaction");
    }

    public CompletableFuture<SendResult<String, Object>> sendEditMessage(Object messageEdit) {
        return sendSafely(chatTopicUpdate, messageEdit, "Edit Message");
    }

    public CompletableFuture<SendResult<String, Object>> sendRevokeMessage(Object messageRevoke) {
        return sendSafely(chatTopicUpdate, messageRevoke, "Revoke Message");
    }

    public CompletableFuture<SendResult<String, Object>> sendStatusMessage(Object statusMsg) {
        return sendSafely(chatTopicUpdate, statusMsg, "Status Message");
    }

    private CompletableFuture<SendResult<String, Object>> sendSafely(String topic, Object payload, String actionName) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(Objects.requireNonNull(topic),
                payload);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} to Kafka topic '{}'", actionName, topic, ex);
            } else {
                log.debug("Published {} to Kafka topic '{}' [offset={}]", actionName, topic, result.getRecordMetadata().offset());
            }
        });
        return future;
    }
}
