package com.web.backend.service;

import com.web.backend.common.NotificationsType;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;

import com.web.backend.model.postgres.UserEntity;

public interface NotificationService {

    CursorResponse<NotificationResponse> getNotifications(UserEntity user, String cursorStr, int size);

    Long getUnreadNotificationCounts(UserEntity user);

    void markNotificationAsRead(UserEntity user, Long notiId);

    int markAllNotificationsAsRead(UserEntity user);

    void createNotification(String senderUsername, String recipientUsername, NotificationsType type,
            String content);

}
