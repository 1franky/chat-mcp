package com.aidatachat.application.exception;

/**
 * Internal, typed failure of the document processing pipeline (extraction/chunking/embedding).
 * Never surfaces over HTTP: {@link com.aidatachat.application.service.DocumentProcessingService}
 * catches it and persists only {@link #reasonCode()} as the document's {@code failureReason} —
 * never a raw message, stack trace, or document content.
 */
public final class DocumentProcessingException extends RuntimeException {

    private final String reasonCode;

    public DocumentProcessingException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public DocumentProcessingException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
