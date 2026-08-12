package com.web.backend.controller.response.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.web.backend.common.SocketEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocketResponse<T> {

    private SocketEventType type;

    private String message;

    private T data;

    public static <T> SocketResponse<T> message(T data) {
        return SocketResponse.<T>builder()
                .type(SocketEventType.MESSAGE)
                .message(null)
                .data(data)
                .build();
    }

    public static <T> SocketResponse<T> error(String message, T data) {
        return SocketResponse.<T>builder()
                .type(SocketEventType.ERROR)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> SocketResponse<T> notifications(String message, T data) {
        return SocketResponse.<T>builder()
                .type(SocketEventType.NOTIFICATIONS)
                .message(message)
                .data(data)
                .build();
    }
}
