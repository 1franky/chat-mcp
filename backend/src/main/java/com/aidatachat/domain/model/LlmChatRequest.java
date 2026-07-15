package com.aidatachat.domain.model;

import java.util.List;
import java.util.Objects;

public record LlmChatRequest(String modelId, List<ChatMessage> messages) {

    public LlmChatRequest {
        Objects.requireNonNull(modelId, "modelId is required");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages are required"));
    }
}
