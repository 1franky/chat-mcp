package com.aidatachat.adapters.out.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.McpCommunicationException;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.McpToolResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class McpGatewayHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private McpHttpClient http;
    private McpDestinationPolicy policy;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new McpHttpClient(Duration.ofSeconds(2), 64 * 1024);
        policy = new McpDestinationPolicy();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void negotiatesSessionAndForwardsSessionIdOnSubsequentCalls() {
        server.createContext(
                "/mcp",
                exchange -> {
                    JsonNode body = readBody(exchange);
                    String method = body.path("method").asText("");
                    String sessionHeader = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
                    switch (method) {
                        case "initialize" -> {
                            exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                            send(exchange, 200, initializeResult(body, "1.0.0-test"));
                        }
                        case "notifications/initialized" -> send(exchange, 202, "");
                        case "tools/list" -> {
                            if (!"session-1".equals(sessionHeader)) {
                                send(exchange, 404, "{}");
                                return;
                            }
                            send(exchange, 200, toolsListResult(body));
                        }
                        case "tools/call" -> {
                            if (!"session-1".equals(sessionHeader)) {
                                send(exchange, 404, "{}");
                                return;
                            }
                            if (isToolCall(body, "health_check")) {
                                send(exchange, 200, healthCheckResult(body, "1.0.0"));
                            } else {
                                send(exchange, 200, toolCallResult(body, false));
                            }
                        }
                        default -> send(exchange, 400, "{}");
                    }
                });
        McpSessionManager session = session();

        assertThat(session.discoverTools())
                .extracting(tool -> tool.name())
                .containsExactly("health_check");
        assertThat(session.status().state()).isEqualTo(IntegrationState.UP);
        assertThat(session.status().contractVersion()).isEqualTo("1.0.0");
        McpToolResult result = session.call("health_check", Map.of());
        assertThat(result.isError()).isFalse();
    }

    @Test
    void marksDownAndClearsToolsWhenContractMajorIsIncompatible() {
        server.createContext(
                "/mcp",
                exchange -> {
                    JsonNode body = readBody(exchange);
                    String method = body.path("method").asText("");
                    switch (method) {
                        case "initialize" -> {
                            exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                            send(exchange, 200, initializeResult(body, "1.0.0-test"));
                        }
                        case "notifications/initialized" -> send(exchange, 202, "");
                        case "tools/call" -> {
                            if (isToolCall(body, "health_check")) {
                                send(exchange, 200, healthCheckResult(body, "2.0.0"));
                            } else {
                                send(exchange, 200, toolCallResult(body, false));
                            }
                        }
                        default -> send(exchange, 400, "{}");
                    }
                });
        McpSessionManager session = session();

        assertThat(session.discoverTools()).isEmpty();
        assertThat(session.status().state()).isEqualTo(IntegrationState.DOWN);
        assertThat(session.status().contractVersion()).isEqualTo("2.0.0");
    }

    @Test
    void reportsUnavailableWhenServerIsUnreachable() throws IOException {
        HttpServer temp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = temp.getAddress().getPort();
        temp.start();
        temp.stop(0);
        McpSessionManager session =
                new McpSessionManager(
                        http,
                        policy,
                        new NoOpMcpAuthProvider(),
                        "http://127.0.0.1:" + port,
                        "/mcp",
                        "2025-11-25",
                        1,
                        "test-client");

        session.refresh();

        assertThat(session.status().state()).isNotEqualTo(IntegrationState.UP);
        assertThatThrownBy(() -> session.call("health_check", Map.of()))
                .isInstanceOf(McpCommunicationException.class);
    }

    @Test
    void neverPresentsAToolErrorAsSuccess() {
        server.createContext(
                "/mcp",
                exchange -> {
                    JsonNode body = readBody(exchange);
                    String method = body.path("method").asText("");
                    switch (method) {
                        case "initialize" -> {
                            exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                            send(exchange, 200, initializeResult(body, "1.0.0-test"));
                        }
                        case "notifications/initialized" -> send(exchange, 202, "");
                        case "tools/list" -> send(exchange, 200, toolsListResult(body));
                        case "tools/call" -> {
                            if (isToolCall(body, "health_check")) {
                                send(exchange, 200, healthCheckResult(body, "1.0.0"));
                            } else {
                                send(exchange, 200, toolCallResult(body, true));
                            }
                        }
                        default -> send(exchange, 400, "{}");
                    }
                });
        McpSessionManager session = session();

        McpToolResult result =
                session.call("execute_read_query", Map.of("sql", "DELETE FROM users"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void recoversFromALostSessionByReinitializingOnce() {
        AtomicInteger toolCallAttempts = new AtomicInteger();
        AtomicInteger sessionCounter = new AtomicInteger();
        server.createContext(
                "/mcp",
                exchange -> {
                    JsonNode body = readBody(exchange);
                    String method = body.path("method").asText("");
                    switch (method) {
                        case "initialize" -> {
                            String sessionId = "session-" + sessionCounter.incrementAndGet();
                            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
                            send(exchange, 200, initializeResult(body, "1.0.0-test"));
                        }
                        case "notifications/initialized" -> send(exchange, 202, "");
                        case "tools/list" -> send(exchange, 200, toolsListResult(body));
                        case "tools/call" -> {
                            if (isToolCall(body, "health_check")) {
                                send(exchange, 200, healthCheckResult(body, "1.0.0"));
                            } else if (toolCallAttempts.incrementAndGet() == 1) {
                                send(exchange, 404, "{}");
                            } else {
                                send(exchange, 200, toolCallResult(body, false));
                            }
                        }
                        default -> send(exchange, 400, "{}");
                    }
                });
        McpSessionManager session = session();

        McpToolResult result = session.call("execute_read_query", Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(sessionCounter.get()).isEqualTo(2);
    }

    private McpSessionManager session() {
        return new McpSessionManager(
                http,
                policy,
                new NoOpMcpAuthProvider(),
                baseUrl,
                "/mcp",
                "2025-11-25",
                1,
                "test-client");
    }

    private boolean isToolCall(JsonNode request, String toolName) {
        return toolName.equals(request.path("params").path("name").asText(""));
    }

    private String initializeResult(JsonNode request, String serverVersion) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"
                + request.path("id").asText("1")
                + ",\"result\":{\"protocolVersion\":\"2025-11-25\",\"serverInfo\":{\"name\":\"test-mcp\",\"version\":\""
                + serverVersion
                + "\"}}}";
    }

    private String healthCheckResult(JsonNode request, String contractVersion) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"
                + request.path("id").asText("1")
                + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"structuredContent\":{\"status\":\"ok\",\"contract_version\":\""
                + contractVersion
                + "\",\"fake\":true},\"isError\":false}}";
    }

    private String toolsListResult(JsonNode request) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"
                + request.path("id").asText("1")
                + ",\"result\":{\"tools\":[{\"name\":\"health_check\",\"description\":\"test\",\"inputSchema\":{\"type\":\"object\"},\"annotations\":{\"readOnlyHint\":true}}]}}";
    }

    private String toolCallResult(JsonNode request, boolean isError) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"
                + request.path("id").asText("1")
                + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"done\"}],\"structuredContent\":{},\"isError\":"
                + isError
                + "}}";
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        return JSON.readTree(exchange.getRequestBody());
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
