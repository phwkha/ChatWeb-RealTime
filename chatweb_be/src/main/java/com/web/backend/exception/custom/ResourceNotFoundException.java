package com.web.backend.exception.custom;

public class ResourceNotFoundException extends RuntimeException {
    private final transient Object requestData;

    public ResourceNotFoundException(String message) {
        super(message);
        this.requestData = null;
    }

    public ResourceNotFoundException(String message, Object requestData) {
        super(message);
        this.requestData = requestData;
    }

    public Object getRequestData() {
        return requestData;
    }
}