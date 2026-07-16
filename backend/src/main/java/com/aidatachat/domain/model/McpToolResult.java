package com.aidatachat.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record McpToolResult(
        String toolName,
        boolean isError,
        List<String> content,
        Map<String, Object> structuredContent) {

    public McpToolResult {
        Objects.requireNonNull(toolName, "toolName is required");
        content = List.copyOf(Objects.requireNonNull(content, "content is required"));
        structuredContent =
                Map.copyOf(
                        Objects.requireNonNull(structuredContent, "structuredContent is required"));
    }
}
