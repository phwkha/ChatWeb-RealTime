package com.web.backend.service;

import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;

public interface NotificationService {

    CursorResponse<NotificationResponse> getNotifications(String username, String cursorStr, int size);

    Long getUnreadNotificationCounts(String username);

    void markNotificationAsRead(String username, Long notiId);

    int markAllNotificationsAsRead(String username);

}
