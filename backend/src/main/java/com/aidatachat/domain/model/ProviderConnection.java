package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProviderConnection(
        UUID id,
        UUID ownerId,
        String displayName,
        ProviderType providerType,
        String baseUrl,
        String region,
        String modelsPath,
        String responsesPath,
        String chatCompletionsPath,
        String configuredModelId,
        String defaultModelId,
        EncryptedProviderCredential credential,
        ProviderConnectionState state,
        String lastErrorCode,
        Instant lastTestedAt,
        Instant lastModelsSyncedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public ProviderConnection {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(providerType, "providerType is required");
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
