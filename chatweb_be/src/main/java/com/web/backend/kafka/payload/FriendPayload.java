package com.web.backend.kafka.payload;

import com.web.backend.common.NotificationsType;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record FriendPayload(
        String eventId,
        Long notificationId,
        String senderUsername,
        String senderDisplayName,
        String recipientUsername,
        String recipientDisplayName,
        List<String> recipientUsernames,
        NotificationsType senderType,
        NotificationsType recipientType) {
    public FriendPayload {
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
    }
}
