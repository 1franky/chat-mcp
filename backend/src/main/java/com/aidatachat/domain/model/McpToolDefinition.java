package com.aidatachat.domain.model;

import java.util.Map;
import java.util.Objects;

public record McpToolDefinition(
        String name, String description, Map<String, Object> inputSchema, boolean readOnly) {

    public McpToolDefinition {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(description, "description is required");
        inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema is required"));
    }
}
