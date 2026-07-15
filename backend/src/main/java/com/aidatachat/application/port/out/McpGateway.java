package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.McpToolResult;
import java.util.List;
import java.util.Map;

public interface McpGateway {

    McpConnectionStatus status();

    List<McpToolDefinition> discoverTools();

    McpToolResult call(String toolName, Map<String, Object> arguments);
}
