package com.web.backend.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.web.backend.common.UpdateMessageType;
import com.web.backend.controller.response.ReadReceiptData;
import com.web.backend.kafka.payload.UpdateMessagePayload;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "UPDATE-MESSAGE-PRODUCER")
public class UpdateMessageProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic.update-message.update}")
    private String chatTopicUpdate;

    @Async
    @EventListener
    public void handleReadReceiptEvent(ReadReceiptData receiptData) {
        if (receiptData == null) {
            return;
        }
        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .relatedUsername(receiptData.getReader())
                .type(UpdateMessageType.STATUS)
                .updateEvent(receiptData)
                .build();
        sendStatusMessage(payload);
    }

    @Async
    @EventListener
    public void handleUpdateMessageEvent(UpdateMessagePayload payload) {
        if (payload == null) {
            return;
        }
        sendSafely(chatTopicUpdate, payload, payload.type() != null ? payload.type().name() : "Update Message");
    }

    public CompletableFuture<SendResult<String, Object>> sendStatusMessage(Object statusMsg) {
        return sendSafely(chatTopicUpdate, statusMsg, "Status Message");
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

    private CompletableFuture<SendResult<String, Object>> sendSafely(String topic, Object payload, String actionName) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                Objects.requireNonNull(topic), payload);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} to Kafka topic '{}'", actionName, topic, ex);
            } else {
                log.debug("Published {} to Kafka topic '{}' [offset={}]", actionName, topic,
                        result.getRecordMetadata().offset());
            }
        });
        return future;
    }
}
