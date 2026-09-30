package com.web.backend.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.web.backend.common.NotificationsType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocketNotificationResponse<T> {

    private Long id;
    private Long notificationId;
    private NotificationsType type;
    private String relatedUsername;
    private String message;
    private T data;

    public static <T> SocketNotificationResponse<T> notificationData(Long id, NotificationsType type,
            String relatedUsername, String message, T data) {
        return SocketNotificationResponse.<T>builder()
                .id(id)
                .notificationId(id)
                .type(type)
                .relatedUsername(relatedUsername)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> SocketNotificationResponse<T> notificationData(Long id, NotificationsType type,
            String relatedUsername, String message) {
        return SocketNotificationResponse.<T>builder()
                .id(id)
                .notificationId(id)
                .type(type)
                .relatedUsername(relatedUsername)
                .message(message)
                .data(null)
                .build();
    }

    public static <T> SocketNotificationResponse<T> notificationData(NotificationsType type, String relatedUsername,
            String message, T data) {
        return notificationData(null, type, relatedUsername, message, data);
    }

    public static <T> SocketNotificationResponse<T> notificationData(NotificationsType type, String relatedUsername,
            String message) {
        return notificationData(null, type, relatedUsername, message, null);
    }

}
