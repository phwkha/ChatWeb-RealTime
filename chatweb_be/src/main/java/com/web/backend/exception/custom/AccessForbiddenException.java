package com.web.backend.exception.custom;

public class AccessForbiddenException extends RuntimeException {
    private final transient Object requestData;

    public AccessForbiddenException(String message) {
        super(message);
        this.requestData = null;
    }

    public AccessForbiddenException(String message, Object requestData) {
        super(message);
        this.requestData = requestData;
    }

    public Object getRequestData() {
        return requestData;
    }
}