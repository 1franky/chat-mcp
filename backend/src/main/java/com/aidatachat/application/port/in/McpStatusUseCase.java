package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import java.util.List;

public interface McpStatusUseCase {

    McpConnectionStatus status();

    List<McpToolDefinition> availableTools();
}
