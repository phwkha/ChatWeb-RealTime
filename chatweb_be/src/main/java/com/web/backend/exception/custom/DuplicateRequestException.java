package com.web.backend.exception.custom;

import lombok.Getter;

@Getter
public class DuplicateRequestException extends RuntimeException {

    private final String messageKey;

    public DuplicateRequestException(String messageKey) {
        super("Duplicate request detected. This request has already been processed.");
        this.messageKey = messageKey;
    }
}
