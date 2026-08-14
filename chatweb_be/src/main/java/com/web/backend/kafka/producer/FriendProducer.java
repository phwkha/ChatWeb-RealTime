package com.web.backend.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.web.backend.kafka.payload.FriendPayload;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "FRIEND-PRODUCER")
public class FriendProducer {

    @Value("${spring.kafka.topic.friend.friend-topic}")
    private String friendTopic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_MUST_NOT_BE_NULL_STRING = "Topic must not be null";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendFriendNoti(FriendPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            kafkaTemplate.send(Objects.requireNonNull(friendTopic, TOPIC_MUST_NOT_BE_NULL_STRING), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish friend notification to Kafka topic '{}'", friendTopic, ex);
                        } else {
                            log.debug("Published friend notification to Kafka topic '{}' [offset={}]", friendTopic,
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Error dispatching friend notification to Kafka topic '{}'", friendTopic, e);
        }
    }
}
