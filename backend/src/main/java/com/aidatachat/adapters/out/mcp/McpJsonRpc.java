package com.aidatachat.adapters.out.mcp;

import com.aidatachat.application.exception.McpCommunicationException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Builds and parses JSON-RPC 2.0 envelopes for the MCP Streamable HTTP contract. */
final class McpJsonRpc {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AtomicLong nextId = new AtomicLong(1);

    ObjectNode initializeRequest(String protocolVersion, String clientName, String clientVersion) {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("protocolVersion", protocolVersion);
        ObjectNode capabilities = params.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", clientName);
        clientInfo.put("version", clientVersion);
        return request("initialize", params);
    }

    ObjectNode initializedNotification() {
        ObjectNode notification = JsonNodeFactory.instance.objectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        return notification;
    }

    ObjectNode toolsListRequest() {
        return request("tools/list", JsonNodeFactory.instance.objectNode());
    }

    ObjectNode toolsCallRequest(String toolName, Map<String, Object> arguments) {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("name", toolName);
        params.set("arguments", JSON.valueToTree(arguments));
        return request("tools/call", params);
    }

    JsonNode result(JsonNode envelope, String fallbackCode) {
        if (envelope == null) {
            throw new McpCommunicationException("MCP_INVALID_RESPONSE", null, false, null);
        }
        if (envelope.has("error")) {
            JsonNode error = envelope.path("error");
            throw new McpCommunicationException(
                    "MCP_PROTOCOL_ERROR",
                    null,
                    false,
                    new IllegalStateException(error.path("message").asText(fallbackCode)));
        }
        JsonNode result = envelope.path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new McpCommunicationException("MCP_INVALID_RESPONSE", null, false, null);
        }
        return result;
    }

    static Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return JSON.convertValue(node, MAP_TYPE);
    }

    private ObjectNode request(String method, ObjectNode params) {
        ObjectNode envelope = JsonNodeFactory.instance.objectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", nextId.getAndIncrement());
        envelope.put("method", method);
        envelope.set("params", params);
        return envelope;
    }
}
