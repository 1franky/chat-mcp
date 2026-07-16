package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ConversationToolCall(
        UUID id,
        UUID messageId,
        int generationRound,
        int sequence,
        String toolName,
        String providerToolCallId,
        Map<String, Object> arguments,
        MessageToolCallStatus status,
        Boolean isError,
        Map<String, Object> result,
        String errorCode,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ConversationToolCall {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(messageId, "messageId is required");
        Objects.requireNonNull(toolName, "toolName is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments is required"));
        result = result == null ? null : Map.copyOf(result);
        if (generationRound < 1) {
            throw new IllegalArgumentException("generationRound must be positive");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
        boolean pendingOrRunning =
                status == MessageToolCallStatus.PENDING || status == MessageToolCallStatus.RUNNING;
        if (pendingOrRunning && completedAt != null) {
            throw new IllegalArgumentException("completedAt must be null while pending or running");
        }
        if (!pendingOrRunning && completedAt == null) {
            throw new IllegalArgumentException("completedAt is required for a terminal status");
        }
    }
}
