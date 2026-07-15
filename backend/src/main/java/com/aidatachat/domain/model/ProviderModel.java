package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProviderModel(
        UUID id,
        UUID providerConnectionId,
        String modelId,
        String displayName,
        ProviderModelOrigin origin,
        ProviderCapabilityProfile capabilities,
        Instant discoveredAt,
        Instant lastValidatedAt) {

    public ProviderModel {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(providerConnectionId, "providerConnectionId is required");
        Objects.requireNonNull(modelId, "modelId is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(capabilities, "capabilities are required");
        Objects.requireNonNull(discoveredAt, "discoveredAt is required");
    }
}
