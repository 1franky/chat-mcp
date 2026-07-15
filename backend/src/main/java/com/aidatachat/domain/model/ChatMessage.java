package com.aidatachat.domain.model;

import java.util.Objects;

public record ChatMessage(String role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(content, "content is required");
    }
}
