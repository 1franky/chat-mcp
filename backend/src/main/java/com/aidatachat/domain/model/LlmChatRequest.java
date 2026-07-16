package com.aidatachat.domain.model;

import java.util.List;
import java.util.Objects;

public record LlmChatRequest(
        String modelId, List<ChatMessage> messages, List<McpToolDefinition> tools) {

    public LlmChatRequest {
        Objects.requireNonNull(modelId, "modelId is required");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages are required"));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools are required"));
    }

    public LlmChatRequest(String modelId, List<ChatMessage> messages) {
        this(modelId, messages, List.of());
    }
}
