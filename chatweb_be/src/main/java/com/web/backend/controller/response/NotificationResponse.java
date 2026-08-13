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
public class NotificationResponse<T> {

    private NotificationsType type;
    private String relatedUsername;
    private String message;
    private T data;

    public static <T> NotificationResponse<T> notificationData(NotificationsType type, String relatedUsername,
            String message, T data) {
        return NotificationResponse.<T>builder()
                .type(type)
                .relatedUsername(relatedUsername)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> NotificationResponse<T> notificationData(NotificationsType type, String relatedUsername,
            String message) {
        return NotificationResponse.<T>builder()
                .type(type)
                .relatedUsername(relatedUsername)
                .message(message)
                .data(null)
                .build();
    }

}
