package com.web.backend.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private static final String ERROR_STRING = "error";
    private static final String SUCCESS_STRING = "success";

    private int code;
    private String status;
    private String message;
    private T data;

    // Helper tạo response thành công
    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return ApiResponse.<T>builder()
                .code(statusCode)
                .status(SUCCESS_STRING)
                .message(message)
                .data(data)
                .build();
    }

    // Helper tạo response lỗi
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .status(ERROR_STRING)
                .message(message)
                .build();
    }

    // Helper tạo response lỗi có kèm dữ liệu (data)
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return ApiResponse.<T>builder()
                .code(code)
                .status(ERROR_STRING)
                .message(message)
                .data(data)
                .build();
    }
}