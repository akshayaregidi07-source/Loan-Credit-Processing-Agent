package com.techvestai.project.exception;

public class UnauthorisedResourceException extends RuntimeException {
    public UnauthorisedResourceException(String message) {
        super(message);
    }
    public UnauthorisedResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
