package com.aidatachat.application.exception;

public final class ProviderCommunicationException extends RuntimeException {

    private final String code;
    private final String providerRequestId;
    private final boolean retryable;

    public ProviderCommunicationException(
            String code, String providerRequestId, boolean retryable, Throwable cause) {
        super("Provider request failed", cause);
        this.code = code;
        this.providerRequestId = providerRequestId;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String providerRequestId() {
        return providerRequestId;
    }

    public boolean retryable() {
        return retryable;
    }
}
