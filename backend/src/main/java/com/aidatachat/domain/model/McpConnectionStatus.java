package com.aidatachat.domain.model;

import java.util.Objects;

public record McpConnectionStatus(
        IntegrationState state,
        String serverVersion,
        String contractVersion,
        String protocolVersion,
        String detail,
        boolean fake) {

    public McpConnectionStatus {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(serverVersion, "serverVersion is required");
        Objects.requireNonNull(contractVersion, "contractVersion is required");
        Objects.requireNonNull(protocolVersion, "protocolVersion is required");
        Objects.requireNonNull(detail, "detail is required");
    }
}
