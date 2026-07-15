package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Conversation(
        UUID id,
        UUID ownerId,
        String title,
        UUID providerConnectionId,
        String modelId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Conversation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(modelId, "modelId is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (title.isBlank() || title.length() > 160) {
            throw new IllegalArgumentException("title length is invalid");
        }
        if (modelId.isBlank() || modelId.length() > 255) {
            throw new IllegalArgumentException("modelId length is invalid");
        }
    }
}
