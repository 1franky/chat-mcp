package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConversationMessage(
        UUID id,
        UUID conversationId,
        long position,
        MessageRole role,
        String content,
        UUID providerConnectionId,
        ProviderType providerType,
        String modelId,
        MessageStatus status,
        Integer inputTokens,
        Integer outputTokens,
        String finishReason,
        String providerRequestId,
        UUID regeneratedFromMessageId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public ConversationMessage {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(conversationId, "conversationId is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(content, "content is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
        if (role == MessageRole.ASSISTANT
                && (providerConnectionId == null || providerType == null || modelId == null)) {
            throw new IllegalArgumentException("assistant provider snapshot is required");
        }
        if (inputTokens != null && inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens cannot be negative");
        }
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens cannot be negative");
        }
    }
}
