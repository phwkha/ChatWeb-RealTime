package com.web.backend.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.web.backend.kafka.payload.EmailPayload;
import com.web.backend.service.EmailService;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private EmailConsumer emailConsumer;

    @Test
    void testConsumeEmailTask_OtpEvent() {
        EmailPayload payload = EmailPayload.createOtpEvent("user@example.com", "John Doe", "123456");

        emailConsumer.consumeEmailTask(payload, acknowledgment);

        verify(emailService).sendOtpEmail("user@example.com", "John Doe", "123456");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testConsumeEmailTask_TextEvent() {
        EmailPayload payload = EmailPayload.createTextEvent("user@example.com", "Hello Subject", "Body Content");

        emailConsumer.consumeEmailTask(payload, acknowledgment);

        verify(emailService).sendTextEmail("user@example.com", "Hello Subject", "Body Content");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testConsumeEmailTask_UnknownType_StillAcknowledges() {
        EmailPayload payload = new EmailPayload("UNKNOWN", "user@example.com", null, null, null, null);

        emailConsumer.consumeEmailTask(payload, acknowledgment);

        verify(emailService, never()).sendOtpEmail(any(), any(), any());
        verify(emailService, never()).sendTextEmail(any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testConsumeEmailTask_EmailServiceThrowsException_DoesNotAcknowledge() {
        EmailPayload payload = EmailPayload.createOtpEvent("user@example.com", "John", "123456");
        doThrow(new RuntimeException("Mail server down"))
                .when(emailService).sendOtpEmail("user@example.com", "John", "123456");

        assertThrows(RuntimeException.class, () -> emailConsumer.consumeEmailTask(payload, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
    }

    private String any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
