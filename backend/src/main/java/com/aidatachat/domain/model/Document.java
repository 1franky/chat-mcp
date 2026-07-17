package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Document(
        UUID id,
        UUID ownerId,
        String originalFilename,
        String storageKey,
        String mimeType,
        long byteSize,
        String contentHash,
        DocumentStatus status,
        String failureReason,
        String embeddingModelId,
        Integer embeddingDimension,
        int chunkCount,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Document {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(originalFilename, "originalFilename is required");
        Objects.requireNonNull(storageKey, "storageKey is required");
        Objects.requireNonNull(mimeType, "mimeType is required");
        Objects.requireNonNull(contentHash, "contentHash is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (originalFilename.isBlank() || originalFilename.length() > 255) {
            throw new IllegalArgumentException("originalFilename length is invalid");
        }
        if (storageKey.isBlank() || storageKey.length() > 255) {
            throw new IllegalArgumentException("storageKey length is invalid");
        }
        if (mimeType.isBlank() || mimeType.length() > 127) {
            throw new IllegalArgumentException("mimeType length is invalid");
        }
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive");
        }
        if (!contentHash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(
                    "contentHash must be a 64-char lowercase hex SHA-256");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount cannot be negative");
        }
        if (embeddingDimension != null && embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        if (status == DocumentStatus.READY
                && (embeddingModelId == null || embeddingDimension == null)) {
            throw new IllegalArgumentException("READY documents require embedding metadata");
        }
    }
}
