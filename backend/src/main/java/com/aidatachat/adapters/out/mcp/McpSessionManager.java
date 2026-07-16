package com.aidatachat.adapters.out.mcp;

import com.aidatachat.application.exception.McpCommunicationException;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.McpToolResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/**
 * Negotiates and maintains a single MCP Streamable HTTP session (initialize -&gt;
 * notifications/initialized -&gt; tools/list), caches the tool catalog, and exposes a
 * never-throwing {@link #status()} so the rest of the application can degrade gracefully when MCP
 * is unavailable.
 */
public final class McpSessionManager {

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final McpHttpClient http;
    private final McpDestinationPolicy destinationPolicy;
    private final McpAuthProvider auth;
    private final McpJsonRpc jsonRpc = new McpJsonRpc();
    private final URI endpoint;
    private final String protocolVersion;
    private final int requiredContractMajor;
    private final String clientVersion;
    private final AtomicReference<McpConnectionStatus> status;
    private final AtomicReference<List<McpToolDefinition>> tools = new AtomicReference<>(List.of());
    private volatile String sessionId;

    public McpSessionManager(
            McpHttpClient http,
            McpDestinationPolicy destinationPolicy,
            McpAuthProvider auth,
            String baseUrl,
            String endpointPath,
            String protocolVersion,
            int requiredContractMajor,
            String clientVersion) {
        this.http = Objects.requireNonNull(http);
        this.destinationPolicy = Objects.requireNonNull(destinationPolicy);
        this.auth = Objects.requireNonNull(auth);
        this.endpoint = append(baseUrl, endpointPath);
        this.protocolVersion = Objects.requireNonNull(protocolVersion);
        this.requiredContractMajor = requiredContractMajor;
        this.clientVersion = Objects.requireNonNull(clientVersion);
        this.status =
                new AtomicReference<>(
                        new McpConnectionStatus(
                                IntegrationState.DOWN,
                                "unknown",
                                "0.0.0",
                                protocolVersion,
                                "MCP client not initialized yet",
                                false));
        destinationPolicy.validate(endpoint);
    }

    public McpConnectionStatus status() {
        return status.get();
    }

    public List<McpToolDefinition> discoverTools() {
        if (tools.get().isEmpty()) {
            refresh();
        }
        return tools.get();
    }

    public McpToolResult call(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName is required");
        Objects.requireNonNull(arguments, "arguments are required");
        ensureSession();
        try {
            return callOnce(toolName, arguments);
        } catch (McpCommunicationException exception) {
            if (!"MCP_SESSION_LOST".equals(exception.code())) {
                throw exception;
            }
            sessionId = null;
            ensureSession();
            return callOnce(toolName, arguments);
        }
    }

    public synchronized void refresh() {
        try {
            ensureSession();
            fetchTools();
        } catch (McpCommunicationException exception) {
            markDown(exception.code());
        } catch (RuntimeException exception) {
            markDown("MCP_UNAVAILABLE");
        }
    }

    private synchronized void ensureSession() {
        if (sessionId != null) {
            return;
        }
        destinationPolicy.validate(endpoint);
        McpHttpClient.JsonResponse response =
                http.post(
                        endpoint,
                        this::headers,
                        jsonRpc.initializeRequest(
                                protocolVersion, "ai-data-chat-backend", clientVersion));
        JsonNode result = jsonRpc.result(response.body(), "MCP_INITIALIZE_FAILED");
        String negotiatedProtocol = result.path("protocolVersion").asText(protocolVersion);
        String serverVersion = result.path("serverInfo").path("version").asText("unknown");
        String contractVersion = result.path("_meta").path("contract_version").asText("0.0.0");
        boolean fake = result.path("_meta").path("fake").asBoolean(false);
        if (response.sessionId() == null) {
            throw new McpCommunicationException("MCP_PROTOCOL_MISMATCH", null, false, null);
        }
        int contractMajor = majorVersion(contractVersion);
        if (contractMajor != requiredContractMajor) {
            status.set(
                    new McpConnectionStatus(
                            IntegrationState.DOWN,
                            serverVersion,
                            contractVersion,
                            negotiatedProtocol,
                            "Contract major mismatch: server="
                                    + contractMajor
                                    + " required="
                                    + requiredContractMajor,
                            fake));
            tools.set(List.of());
            throw new McpCommunicationException("MCP_CONTRACT_MISMATCH", null, false, null);
        }
        sessionId = response.sessionId();
        http.post(endpoint, this::sessionHeaders, jsonRpc.initializedNotification());
        status.set(
                new McpConnectionStatus(
                        IntegrationState.UP,
                        serverVersion,
                        contractVersion,
                        negotiatedProtocol,
                        "Connected",
                        fake));
    }

    private void fetchTools() {
        McpHttpClient.JsonResponse response =
                http.post(endpoint, this::sessionHeaders, jsonRpc.toolsListRequest());
        JsonNode result = jsonRpc.result(response.body(), "MCP_TOOLS_LIST_FAILED");
        JsonNode toolsNode = result.path("tools");
        List<McpToolDefinition> discovered = new ArrayList<>();
        if (toolsNode.isArray()) {
            for (JsonNode tool : toolsNode) {
                discovered.add(
                        new McpToolDefinition(
                                tool.path("name").asText(""),
                                tool.path("description").asText(""),
                                McpJsonRpc.toMap(tool.path("inputSchema")),
                                tool.path("annotations").path("readOnlyHint").asBoolean(false)));
            }
        }
        tools.set(List.copyOf(discovered));
        McpConnectionStatus current = status.get();
        status.set(
                new McpConnectionStatus(
                        IntegrationState.UP,
                        current.serverVersion(),
                        current.contractVersion(),
                        current.protocolVersion(),
                        "Connected",
                        current.fake()));
    }

    private McpToolResult callOnce(String toolName, Map<String, Object> arguments) {
        McpHttpClient.JsonResponse response =
                http.post(
                        endpoint,
                        this::sessionHeaders,
                        jsonRpc.toolsCallRequest(toolName, arguments));
        JsonNode result = jsonRpc.result(response.body(), "MCP_TOOL_CALL_FAILED");
        boolean isError = result.path("isError").asBoolean(false);
        List<String> content = new ArrayList<>();
        JsonNode contentNode = result.path("content");
        if (contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                if ("text".equals(item.path("type").asText(""))) {
                    content.add(item.path("text").asText(""));
                }
            }
        }
        Map<String, Object> structuredContent = McpJsonRpc.toMap(result.path("structuredContent"));
        return new McpToolResult(toolName, isError, content, structuredContent);
    }

    private void headers(HttpHeaders headers) {
        auth.headers().accept(headers);
    }

    private void sessionHeaders(HttpHeaders headers) {
        headers(headers);
        String currentSession = sessionId;
        if (currentSession == null) {
            throw new McpCommunicationException("MCP_SESSION_LOST", null, true, null);
        }
        headers.set(SESSION_HEADER, currentSession);
    }

    private void markDown(String code) {
        McpConnectionStatus current = status.get();
        status.set(
                new McpConnectionStatus(
                        IntegrationState.DOWN,
                        current.serverVersion(),
                        current.contractVersion(),
                        current.protocolVersion(),
                        code,
                        current.fake()));
        tools.set(List.of());
        sessionId = null;
    }

    private int majorVersion(String version) {
        int dot = version.indexOf('.');
        String majorPart = dot >= 0 ? version.substring(0, dot) : version;
        try {
            return Integer.parseInt(majorPart);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static URI append(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + suffix);
    }
}
