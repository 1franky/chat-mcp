package com.aidatachat.domain.model;

import java.util.Objects;

public record ProviderProbeResult(
        boolean success, String code, String message, String providerRequestId, boolean retryable) {

    public ProviderProbeResult {
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(message, "message is required");
    }

    public static ProviderProbeResult success(String providerRequestId) {
        return new ProviderProbeResult(
                true, "CONNECTION_OK", "Conexion verificada.", providerRequestId, false);
    }

    public static ProviderProbeResult failure(
            String code, String message, String providerRequestId, boolean retryable) {
        return new ProviderProbeResult(false, code, message, providerRequestId, retryable);
    }
}
