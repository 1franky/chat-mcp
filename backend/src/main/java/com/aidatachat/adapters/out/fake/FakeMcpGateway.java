package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.McpToolResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FakeMcpGateway implements McpGateway {

    public static final String CONTRACT_VERSION = "1.0.0";
    public static final String PROTOCOL_VERSION = "2025-11-25";

    private static final Set<String> ALLOWED_TOOLS = Set.of("health_check", "hello_world");

    @Override
    public McpConnectionStatus status() {
        return new McpConnectionStatus(
                IntegrationState.UP,
                "0.0.0-fake",
                CONTRACT_VERSION,
                PROTOCOL_VERSION,
                "Fake local; no representa el MCP externo real",
                true);
    }

    @Override
    public List<McpToolDefinition> discoverTools() {
        Map<String, Object> noArguments =
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
        Map<String, Object> helloArguments =
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("name", Map.of("type", "string", "maxLength", 100)),
                        "additionalProperties",
                        false);

        return List.of(
                new McpToolDefinition(
                        "health_check", "Returns deterministic fake health", noArguments, true),
                new McpToolDefinition(
                        "hello_world", "Returns a deterministic greeting", helloArguments, true));
    }

    @Override
    public McpToolResult call(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName is required");
        Objects.requireNonNull(arguments, "arguments are required");
        if (!ALLOWED_TOOLS.contains(toolName)) {
            throw new IllegalArgumentException(
                    "Tool is not allowed by the fake gateway: " + toolName);
        }

        return switch (toolName) {
            case "health_check" ->
                    new McpToolResult(
                            toolName,
                            false,
                            List.of("Fake MCP is healthy"),
                            Map.of(
                                    "status",
                                    "ok",
                                    "contract_version",
                                    CONTRACT_VERSION,
                                    "fake",
                                    true));
            case "hello_world" -> {
                Object name = arguments.getOrDefault("name", "mundo");
                String message = "Hola, " + name + ".";
                yield new McpToolResult(
                        toolName,
                        false,
                        List.of(message),
                        Map.of("message", message, "fake", true));
            }
            default -> throw new IllegalStateException("Allowlist and switch are inconsistent");
        };
    }
}
