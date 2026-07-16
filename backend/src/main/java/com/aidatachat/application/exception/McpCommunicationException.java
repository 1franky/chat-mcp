package com.aidatachat.application.exception;

public final class McpCommunicationException extends RuntimeException {

    private final String code;
    private final String mcpRequestId;
    private final boolean retryable;

    public McpCommunicationException(
            String code, String mcpRequestId, boolean retryable, Throwable cause) {
        super("MCP request failed", cause);
        this.code = code;
        this.mcpRequestId = mcpRequestId;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String mcpRequestId() {
        return mcpRequestId;
    }

    public boolean retryable() {
        return retryable;
    }
}
