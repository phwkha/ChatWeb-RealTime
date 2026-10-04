package com.web.backend.kafka.consumer;

import java.time.Duration;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import com.web.backend.kafka.payload.EmailPayload;

import org.springframework.stereotype.Component;

import com.web.backend.service.ConsumerDeduplicationService;
import com.web.backend.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL-KAFKA-CONSUMER")
public class EmailConsumer {

    private final EmailService emailService;
    private final ConsumerDeduplicationService dedupService;

    private static final String CONSUMER_NAME = "email";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private static final String OTP_STRING = "OTP";

    private static final String TEXT_STRING = "TEXT";

    @RetryableTopic(attempts = "10", backoff = @Backoff(delay = 30000), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, dltStrategy = DltStrategy.NO_DLT, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.email.email-topic}", groupId = "${spring.kafka.topic.email.group-id}", containerFactory = "emailKafkaListenerContainerFactory")
    public void consumeEmailTask(EmailPayload emailEvent, Acknowledgment ack) {
        if (emailEvent == null || emailEvent.to() == null || emailEvent.to().isBlank()) {
            if (ack != null)
                ack.acknowledge();
            return;
        }

        String eventId = emailEvent.eventId();

        if (dedupService.isDuplicate(CONSUMER_NAME, eventId, DEDUP_TTL)) {
            log.info("Email event '{}' already processed. Skipping and committing offset.", eventId);
            if (ack != null)
                ack.acknowledge();
            return;
        }

        log.debug("Consumed email task: type='{}', recipient='{}'", emailEvent.type(), emailEvent.to());

        try {
            if (OTP_STRING.equals(emailEvent.type())) {
                emailService.sendOtpEmail(emailEvent.to(), emailEvent.name(), emailEvent.otp());
            } else if (TEXT_STRING.equals(emailEvent.type())) {
                emailService.sendTextEmail(emailEvent.to(), emailEvent.subject(), emailEvent.content());
            }

            if (ack != null)
                ack.acknowledge();
            log.info("Email task processed successfully for recipient '{}'", emailEvent.to());

        } catch (Exception ex) {
            dedupService.clearOnFailure(CONSUMER_NAME, eventId);
            log.error("Failed to process email task '{}', delegating to retry: {}", eventId, ex.getMessage());
            throw ex;
        }
    }
}
