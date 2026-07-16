package com.aidatachat.adapters.out.mcp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.McpCommunicationException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class McpDestinationPolicyTest {

    private final McpDestinationPolicy policy = new McpDestinationPolicy();

    @Test
    void allowsHttpAndHttpsDestinationsWithoutUserInfoOrQuery() {
        assertThatCode(() -> policy.validate(URI.create("http://127.0.0.1:8000/mcp")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(URI.create("https://127.0.0.1:8443/mcp")))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksUserInfoQueryAndFragment() {
        assertBlocked("http://user:pass@127.0.0.1:8000/mcp");
        assertBlocked("http://127.0.0.1:8000/mcp?debug=true");
        assertBlocked("http://127.0.0.1:8000/mcp#section");
    }

    @Test
    void blocksUnsupportedSchemes() {
        assertBlocked("ftp://127.0.0.1:8000/mcp");
    }

    @Test
    void blocksLinkLocalMetadataAddresses() {
        assertBlocked("http://169.254.169.254/latest/meta-data");
    }

    private void assertBlocked(String uri) {
        assertThatThrownBy(() -> policy.validate(URI.create(uri)))
                .isInstanceOf(McpCommunicationException.class)
                .extracting(exception -> ((McpCommunicationException) exception).code())
                .isEqualTo("MCP_DESTINATION_BLOCKED");
    }
}
