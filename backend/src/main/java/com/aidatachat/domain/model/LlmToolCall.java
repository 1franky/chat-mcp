package com.aidatachat.domain.model;

import java.util.Map;
import java.util.Objects;

public record LlmToolCall(String id, String name, Map<String, Object> arguments) {

    public LlmToolCall {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(name, "name is required");
        arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments is required"));
    }
}
