package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import com.web.backend.kafka.payload.EmailPayload;

import org.springframework.retry.annotation.Backoff;

import org.springframework.stereotype.Component;

import com.web.backend.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL-KAFKA-CONSUMER")
public class EmailConsumer {

    private final EmailService emailService;

    private static final String EMAILKAFKALISTENERCONTAINERFACTORY_STRING = "emailKafkaListenerContainerFactory";

    private static final String OTP_STRING = "OTP";

    private static final String TEXT_STRING = "TEXT";

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000), autoCreateTopics = "true", topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "${spring.kafka.topic.email.email-topic}", groupId = "${spring.kafka.topic.email.group-id}", containerFactory = EMAILKAFKALISTENERCONTAINERFACTORY_STRING)
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
