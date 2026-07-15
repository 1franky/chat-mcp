package com.aidatachat.domain.model;

import java.util.Objects;

public record DiscoveredProviderModel(
        String modelId, String displayName, ProviderCapabilityProfile capabilities) {

    public DiscoveredProviderModel {
        Objects.requireNonNull(modelId, "modelId is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(capabilities, "capabilities are required");
    }
}
