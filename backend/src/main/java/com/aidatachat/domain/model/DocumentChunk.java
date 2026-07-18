package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DocumentChunk(
        UUID id,
        UUID documentId,
        UUID ownerId,
        int chunkIndex,
        String content,
        Integer pageNumber,
        String sectionLabel,
        String embeddingModelId,
        float[] embedding,
        Instant createdAt) {

    public DocumentChunk {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(documentId, "documentId is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(content, "content is required");
        Objects.requireNonNull(embeddingModelId, "embeddingModelId is required");
        Objects.requireNonNull(embedding, "embedding is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex cannot be negative");
        }
        if (content.isEmpty() || content.length() > 8000) {
            throw new IllegalArgumentException("content length is invalid");
        }
        if (pageNumber != null && pageNumber <= 0) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (sectionLabel != null && (sectionLabel.isBlank() || sectionLabel.length() > 255)) {
            throw new IllegalArgumentException("sectionLabel length is invalid");
        }
        if (embeddingModelId.isBlank() || embeddingModelId.length() > 255) {
            throw new IllegalArgumentException("embeddingModelId length is invalid");
        }
        if (embedding.length == 0) {
            throw new IllegalArgumentException("embedding cannot be empty");
        }
    }
}
