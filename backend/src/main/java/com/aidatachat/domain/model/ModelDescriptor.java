package com.aidatachat.domain.model;

import java.util.Objects;

public record ModelDescriptor(
        String id, String displayName, String origin, ProviderCapabilities capabilities) {

    public ModelDescriptor {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(capabilities, "capabilities are required");
    }
}
