package com.aidatachat.domain.model;

import java.util.List;
import java.util.Objects;

public record ChatMessage(
        String role,
        String content,
        List<LlmToolCall> toolCalls,
        String toolCallId,
        String toolName) {

    public ChatMessage {
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(content, "content is required");
        toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls is required"));
    }

    public ChatMessage(String role, String content) {
        this(role, content, List.of(), null, null);
    }
}
