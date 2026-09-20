package com.web.backend.controller.response;

import java.time.Instant;

import com.web.backend.common.NotificationTargetType;
import com.web.backend.common.NotificationsType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationsType type;
    private NotificationTargetType targetType;
    private String targetId;
    private String content;
    private Boolean isRead;
    private Instant createdAt;
    private String senderUsername;
    private String senderFirstName;
    private String senderLastName;
    private String senderAvatar;
}
