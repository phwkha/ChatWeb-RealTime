package com.web.backend.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    INVALID_INPUT(400),
    BAD_FORMAT(400),
    INVALID_DATA(400),
    CONSTRAINT_VIOLATION(400),
    ILLEGAL_ARGUMENT(400),
    ILLEGAL_STATE(400),
    STOMP_ERROR(400),

    UNAUTHORIZED(401),
    TOKEN_EXPIRED(401),
    TOKEN_INVALID(401),

    ACCESS_FORBIDDEN(403),
    ACCESS_DENIED(403),

    RESOURCE_NOT_FOUND(404),

    RESOURCE_CONFLICT(409),

    PAYLOAD_TOO_LARGE(413),

    RATE_LIMITED(429),

    INTERNAL_SERVER_ERROR(500),
    PROCESSING_ERROR(500),

    SYSTEM_OVERLOAD(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }
}
