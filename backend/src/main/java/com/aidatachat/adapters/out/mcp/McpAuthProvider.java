package com.aidatachat.adapters.out.mcp;

import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * Extension point for future MCP authentication (bearer token, OAuth, mTLS). Data Platform MCP
 * has no authentication today; {@link NoOpMcpAuthProvider} is the only implementation.
 */
public interface McpAuthProvider {

    Consumer<HttpHeaders> headers();
}
