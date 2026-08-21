package com.web.backend.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

import com.web.backend.common.UpdateMessageType;
import com.web.backend.controller.response.ReadReceiptResponse;
import com.web.backend.kafka.payload.UpdateMessagePayload;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "UPDATE-MESSAGE-PRODUCER")
public class UpdateMessageProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic.update-message.update}")
    private String chatTopicUpdate;

    private static final String TOPIC_MUST_NOT_BE_NULL_STRING = "Topic name must not be null";

    @Async
    @EventListener
    public void handleReadReceiptEvent(ReadReceiptResponse receiptData) {
        if (receiptData == null) {
            return;
        }
        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .relatedUsername(receiptData.getReader())
                .type(UpdateMessageType.STATUS)
                .updateEvent(receiptData)
                .build();
        sendUpdateMessage(payload);
    }

    @Async
    @EventListener
    public void handleUpdateMessageEvent(UpdateMessagePayload payload) {
        sendUpdateMessage(payload);
    }

    public void sendUpdateMessage(UpdateMessagePayload payload) {
        if (payload == null) {
            return;
        }
        try {
            kafkaTemplate.send(Objects.requireNonNull(chatTopicUpdate, TOPIC_MUST_NOT_BE_NULL_STRING), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish update message event [type='{}', user='{}'] to Kafka topic '{}'",
                                    payload.type(), payload.relatedUsername(), chatTopicUpdate, ex);
                        } else {
                            log.debug("Published update message event [type='{}', user='{}'] to Kafka topic '{}' [offset={}]",
                                    payload.type(), payload.relatedUsername(), chatTopicUpdate,
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Error dispatching update message event to Kafka topic '{}'", chatTopicUpdate, e);
        }
    }
}
