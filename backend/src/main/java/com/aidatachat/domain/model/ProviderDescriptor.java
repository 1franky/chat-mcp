package com.aidatachat.domain.model;

import java.util.Objects;

public record ProviderDescriptor(
        String id,
        String displayName,
        String providerType,
        IntegrationState state,
        ProviderCapabilities capabilities,
        boolean fake) {

    public ProviderDescriptor {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(providerType, "providerType is required");
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(capabilities, "capabilities are required");
    }
}
