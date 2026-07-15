package com.techvestai.project.exception;

public class ScoringException extends RuntimeException {
    private final String errorCode;

    public ScoringException(String message) {
        super(message);
        this.errorCode = null;
    }

    public ScoringException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
