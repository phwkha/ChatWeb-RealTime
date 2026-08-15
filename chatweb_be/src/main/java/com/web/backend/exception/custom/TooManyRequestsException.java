package com.web.backend.exception.custom;

import lombok.Getter;

@Getter
public class TooManyRequestsException extends RuntimeException {

    private final String messageKey;
    private final long retryAfterSeconds;

    public TooManyRequestsException(String messageKey, long retryAfterSeconds) {
        super("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.");
        this.messageKey = messageKey;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public TooManyRequestsException(String messageKey) {
        this(messageKey, 60);
    }
}
