package com.aidatachat.domain.model;

import java.util.Map;
import java.util.Objects;

public record McpToolResult(
        String toolName, boolean successful, Map<String, Object> structuredContent) {

    public McpToolResult {
        Objects.requireNonNull(toolName, "toolName is required");
        structuredContent =
                Map.copyOf(
                        Objects.requireNonNull(structuredContent, "structuredContent is required"));
    }
}
