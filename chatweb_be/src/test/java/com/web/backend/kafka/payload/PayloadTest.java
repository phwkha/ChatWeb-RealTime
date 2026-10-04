package com.web.backend.kafka.payload;

import com.web.backend.common.NotificationsType;
import com.web.backend.common.UpdateMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadTest {

    @Test
    void emailPayload_NullOrBlankEventId_GeneratesUuid() {
        EmailPayload payload1 = new EmailPayload(null, "OTP", "user@test.com", "User", "123456", null, null);
        assertNotNull(payload1.eventId());
        assertFalse(payload1.eventId().isBlank());

        EmailPayload payload2 = new EmailPayload("   ", "OTP", "user@test.com", "User", "123456", null, null);
        assertNotNull(payload2.eventId());
        assertFalse(payload2.eventId().isBlank());
    }

    @Test
    void emailPayload_CustomEventId_Preserved() {
        EmailPayload payload = new EmailPayload("custom-evt-1", "OTP", "user@test.com", "User", "123456", null, null);
        assertEquals("custom-evt-1", payload.eventId());
    }

    @Test
    void emailPayload_FactoryMethods_GenerateValidPayloads() {
        EmailPayload otp = EmailPayload.createOtpEvent("user@test.com", "User", "123456");
        assertNotNull(otp.eventId());
        assertEquals("OTP", otp.type());
        assertEquals("user@test.com", otp.to());

        EmailPayload text = EmailPayload.createTextEvent("user@test.com", "Subject", "Content");
        assertNotNull(text.eventId());
        assertEquals("TEXT", text.type());
        assertEquals("Subject", text.subject());

        EmailPayload legacy = new EmailPayload("TEXT", "user@test.com", null, null, "Subj", "Cont");
        assertNotNull(legacy.eventId());
        assertEquals("TEXT", legacy.type());
    }

    @Test
    void friendPayload_BuilderWithoutEventId_GeneratesUuid() {
        FriendPayload payload = FriendPayload.builder()
                .notificationId(1L)
                .senderUsername("alice")
                .recipientUsername("bob")
                .recipientType(NotificationsType.FRIEND_REQUEST)
                .build();

        assertNotNull(payload.eventId());
        assertFalse(payload.eventId().isBlank());
        assertEquals("alice", payload.senderUsername());
    }

    @Test
    void friendPayload_BuilderWithCustomEventId_Preserved() {
        FriendPayload payload = FriendPayload.builder()
                .eventId("friend-evt-123")
                .senderUsername("alice")
                .recipientUsername("bob")
                .build();

        assertEquals("friend-evt-123", payload.eventId());
    }

    @Test
    void updateMessagePayload_BuilderWithoutEventId_GeneratesUuid() {
        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .type(UpdateMessageType.STATUS)
                .relatedUsername("alice")
                .build();

        assertNotNull(payload.eventId());
        assertFalse(payload.eventId().isBlank());
        assertEquals(UpdateMessageType.STATUS, payload.type());
    }

    @Test
    void updateMessagePayload_BuilderWithCustomEventId_Preserved() {
        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .eventId("update-evt-999")
                .type(UpdateMessageType.STATUS)
                .build();

        assertEquals("update-evt-999", payload.eventId());
    }
}
