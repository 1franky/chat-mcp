package com.aidatachat.domain.model;

import java.util.Objects;

public record SystemBootstrapStatus(
        String application,
        String version,
        int sprint,
        String mode,
        ProviderDescriptor llmProvider,
        McpConnectionStatus mcp) {

    public SystemBootstrapStatus {
        Objects.requireNonNull(application, "application is required");
        Objects.requireNonNull(version, "version is required");
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(llmProvider, "llmProvider is required");
        Objects.requireNonNull(mcp, "mcp is required");
    }
}
