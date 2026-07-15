package com.techvestai.project.exception;

public class DocumentValidationException extends RuntimeException {
    private final String documentIdentifier;

    public DocumentValidationException(String message) {
        super(message);
        this.documentIdentifier = null;
    }

    public DocumentValidationException(String message, Throwable cause) {
        super(message, cause);
        this.documentIdentifier = null;
    }

    public DocumentValidationException(String message, String documentIdentifier) {
        super(message);
        this.documentIdentifier = documentIdentifier;
    }

    public String getDocumentIdentifier() { return documentIdentifier; }
}
