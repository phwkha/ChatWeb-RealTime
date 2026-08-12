package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.web.backend.kafka.payload.EmailPayload;
import com.web.backend.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.support.Acknowledgment;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL-KAFKA-CONSUMER")
public class EmailConsumer {

    private final EmailService emailService;

    private static final String EMAILKAFKALISTENERCONTAINERFACTORY_STRING = "emailKafkaListenerContainerFactory";

    private static final String OTP_STRING = "OTP";

    private static final String TEXT_STRING = "TEXT";

    @KafkaListener(topics = "${spring.kafka.topic.email.email-topic}", groupId = "${spring.kafka.topic.email.group-id}", containerFactory = EMAILKAFKALISTENERCONTAINERFACTORY_STRING)
    public void consumeEmailTask(EmailPayload event, Acknowledgment ack) {
        log.info("Kafka Consumer received email task of type {} for: {}", event.type(), event.to());

        if (OTP_STRING.equals(event.type())) {
            emailService.sendOtpEmail(event.to(), event.name(), event.otp());
        } else if (TEXT_STRING.equals(event.type())) {
            emailService.sendTextEmail(event.to(), event.subject(), event.content());
        }

        ack.acknowledge();
    }
}
