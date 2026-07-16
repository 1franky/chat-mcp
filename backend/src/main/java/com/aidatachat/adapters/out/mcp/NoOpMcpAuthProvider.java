package com.aidatachat.adapters.out.mcp;

import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

public final class NoOpMcpAuthProvider implements McpAuthProvider {

    @Override
    public Consumer<HttpHeaders> headers() {
        return headers -> {};
    }
}
