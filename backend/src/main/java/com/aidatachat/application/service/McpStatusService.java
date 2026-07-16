package com.aidatachat.application.service;

import com.aidatachat.application.port.in.McpStatusUseCase;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import java.util.List;
import java.util.Objects;

public final class McpStatusService implements McpStatusUseCase {

    private final McpGateway mcp;

    public McpStatusService(McpGateway mcp) {
        this.mcp = Objects.requireNonNull(mcp);
    }

    @Override
    public McpConnectionStatus status() {
        return mcp.status();
    }

    @Override
    public List<McpToolDefinition> availableTools() {
        return mcp.discoverTools();
    }
}
