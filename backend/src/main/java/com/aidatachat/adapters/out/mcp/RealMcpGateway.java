package com.aidatachat.adapters.out.mcp;

import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.McpToolResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RealMcpGateway implements McpGateway {

    private final McpSessionManager session;

    public RealMcpGateway(McpSessionManager session) {
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public McpConnectionStatus status() {
        return session.status();
    }

    @Override
    public List<McpToolDefinition> discoverTools() {
        return session.discoverTools();
    }

    @Override
    public McpToolResult call(String toolName, Map<String, Object> arguments) {
        return session.call(toolName, arguments);
    }
}
