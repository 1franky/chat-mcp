package com.aidatachat.application.service;

import com.aidatachat.application.port.in.SystemStatusUseCase;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.domain.model.SystemBootstrapStatus;
import java.util.Objects;

public final class SystemStatusService implements SystemStatusUseCase {

    private final LlmProviderPort llmProvider;
    private final McpGateway mcpGateway;
    private final String version;
    private final String mode;

    public SystemStatusService(
            LlmProviderPort llmProvider, McpGateway mcpGateway, String version, String mode) {
        this.llmProvider = Objects.requireNonNull(llmProvider);
        this.mcpGateway = Objects.requireNonNull(mcpGateway);
        this.version = Objects.requireNonNull(version);
        this.mode = Objects.requireNonNull(mode);
    }

    @Override
    public SystemBootstrapStatus currentStatus() {
        return new SystemBootstrapStatus(
                "AI Data Chat", version, 0, mode, llmProvider.descriptor(), mcpGateway.status());
    }
}
