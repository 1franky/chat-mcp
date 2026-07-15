package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FakeMcpGatewayTest {

    private final FakeMcpGateway gateway = new FakeMcpGateway();

    @Test
    void exposesOnlyTheTwoDocumentedFakeTools() {
        assertThat(gateway.discoverTools())
                .extracting(tool -> tool.name())
                .containsExactly("health_check", "hello_world");
        assertThat(gateway.status().fake()).isTrue();
        assertThat(gateway.status().protocolVersion()).isEqualTo("2025-11-25");
        assertThat(gateway.status().contractVersion()).isEqualTo("1.0.0");
    }

    @Test
    void returnsStructuredDeterministicHealth() {
        assertThat(gateway.call("health_check", Map.of()).structuredContent())
                .containsEntry("status", "ok")
                .containsEntry("fake", true);
    }

    @Test
    void rejectsToolsOutsideTheAllowlist() {
        assertThatThrownBy(() -> gateway.call("execute_read_query", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }
}
