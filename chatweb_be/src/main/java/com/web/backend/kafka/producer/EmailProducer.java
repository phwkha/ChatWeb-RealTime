package com.web.backend.kafka.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.web.backend.kafka.payload.EmailPayload;

import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL-KAFKA-PRODUCER")
public class EmailProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic.email.email-topic}")
    private String emailTopic;

    public void sendOtpEmailTask(String to, String name, String otp) {
        log.debug("Dispatching OTP email task to Kafka for recipient '{}'", to);
        EmailPayload event = EmailPayload.createOtpEvent(to, name, otp);
        kafkaTemplate.send(Objects.requireNonNull(emailTopic), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OTP email task to Kafka topic '{}' for recipient '{}'", emailTopic, to, ex);
            } else {
                log.debug("Published OTP email task to Kafka topic '{}' for recipient '{}' [offset={}]", emailTopic, to, result.getRecordMetadata().offset());
            }
        });
    }

    public void sendTextEmailTask(String to, String subject, String content) {
        log.debug("Dispatching text email task to Kafka for recipient '{}'", to);
        EmailPayload event = EmailPayload.createTextEvent(to, subject, content);
        kafkaTemplate.send(Objects.requireNonNull(emailTopic), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish text email task to Kafka topic '{}' for recipient '{}'", emailTopic, to, ex);
            } else {
                log.debug("Published text email task to Kafka topic '{}' for recipient '{}' [offset={}]", emailTopic, to, result.getRecordMetadata().offset());
            }
        });
    }
}
