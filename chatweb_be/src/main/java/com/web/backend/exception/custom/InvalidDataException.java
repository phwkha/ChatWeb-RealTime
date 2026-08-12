package com.web.backend.exception.custom;

public class InvalidDataException extends RuntimeException {
    private final transient Object requestData;

    public InvalidDataException(String message) {
        super(message);
        this.requestData = null;
    }

    public InvalidDataException(String message, Object requestData) {
        super(message);
        this.requestData = requestData;
    }

    public Object getRequestData() {
        return requestData;
    }
}
