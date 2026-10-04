package com.web.backend.kafka.payload;

import java.util.UUID;

public record EmailPayload(
        String eventId,
        String type,
        String to,
        String name,
        String otp,
        String subject,
        String content) {

    private static final String OTP_STRING = "OTP";
    private static final String TEXT_STRING = "TEXT";

    public EmailPayload {
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
    }

    public EmailPayload(String type, String to, String name, String otp, String subject, String content) {
        this(UUID.randomUUID().toString(), type, to, name, otp, subject, content);
    }

    public static EmailPayload createOtpEvent(String to, String name, String otp) {
        return new EmailPayload(UUID.randomUUID().toString(), OTP_STRING, to, name, otp, null, null);
    }

    public static EmailPayload createTextEvent(String to, String subject, String content) {
        return new EmailPayload(UUID.randomUUID().toString(), TEXT_STRING, to, null, null, subject, content);
    }
}