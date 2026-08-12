package com.web.backend.kafka.payload;

import com.web.backend.common.NotificationsType;
import lombok.Builder;

import java.util.List;

@Builder
public record FriendPayload(
                String senderUsername,
                String senderDisplayName,
                String recipientUsername,
                String recipientDisplayName,
                List<String> recipientUsernames,
                String destination,
                NotificationsType senderType,
                NotificationsType recipientType) {
}
