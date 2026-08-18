package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import com.web.backend.kafka.payload.EmailPayload;

import org.springframework.stereotype.Component;

import com.web.backend.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL-KAFKA-CONSUMER")
public class EmailConsumer {

    private final EmailService emailService;

    private static final String OTP_STRING = "OTP";

    private static final String TEXT_STRING = "TEXT";

    @RetryableTopic(attempts = "10", backoff = @Backoff(delay = 30000), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.email.email-topic}", groupId = "${spring.kafka.topic.email.group-id}", containerFactory = "emailKafkaListenerContainerFactory")
    public void consumeEmailTask(EmailPayload emailEvent, Acknowledgment ack) {
        log.debug("Consumed email task: type='{}', recipient='{}'", emailEvent.type(), emailEvent.to());

        if (OTP_STRING.equals(emailEvent.type())) {
            emailService.sendOtpEmail(emailEvent.to(), emailEvent.name(), emailEvent.otp());
        } else if (TEXT_STRING.equals(emailEvent.type())) {
            emailService.sendTextEmail(emailEvent.to(), emailEvent.subject(), emailEvent.content());
        }

        ack.acknowledge();
        log.info("Email task processed successfully for recipient '{}' [type={}]", emailEvent.to(), emailEvent.type());
    }

    @DltHandler
    public void handleEmailDlt(EmailPayload emailEvent, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Dead Letter Topic: Failed to process email task for recipient '{}' [topic={}]", emailEvent.to(),
                topic);
    }
}
