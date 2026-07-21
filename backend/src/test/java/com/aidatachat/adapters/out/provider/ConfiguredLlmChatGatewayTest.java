package com.aidatachat.adapters.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidatachat.application.port.out.CredentialCipherPort;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.ProviderConnectionRepository;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderConnection;
import com.aidatachat.domain.model.ProviderConnectionState;
import com.aidatachat.domain.model.ProviderModel;
import com.aidatachat.domain.model.ProviderModelOrigin;
import com.aidatachat.domain.model.ProviderType;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConfiguredLlmChatGatewayTest {

    private static final Set<ProviderType> EXPECTED_TOOL_CALLING_PROVIDERS =
            EnumSet.of(
                    ProviderType.OPENAI,
                    ProviderType.ANTHROPIC,
                    ProviderType.MINIMAX,
                    ProviderType.BYTEPLUS,
                    ProviderType.OPENAI_COMPATIBLE);

    @Test
    void offersToolsOnlyToProvidersThatSupportToolCalling() {
        for (ProviderType providerType : ProviderType.values()) {
            if (providerType == ProviderType.FAKE) {
                continue;
            }
            List<McpToolDefinition> capturedTools = streamAndCaptureTools(providerType);
            if (EXPECTED_TOOL_CALLING_PROVIDERS.contains(providerType)) {
                assertThat(capturedTools).as("tools offered to %s", providerType).hasSize(1);
            } else {
                assertThat(capturedTools).as("tools offered to %s", providerType).isEmpty();
            }
        }
    }

    private List<McpToolDefinition> streamAndCaptureTools(ProviderType providerType) {
        UUID ownerId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();
        ProviderConnection connection =
                new ProviderConnection(
                        connectionId,
                        ownerId,
                        "test",
                        providerType,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ProviderConnectionState.UP,
                        null,
                        null,
                        null,
                        0,
                        now,
                        now);
        ProviderModel model =
                new ProviderModel(
                        UUID.randomUUID(),
                        connectionId,
                        "model-test",
                        "Model Test",
                        ProviderModelOrigin.MANUAL,
                        ProviderCapabilityProfile.unknown(),
                        now,
                        now);

        ProviderConnectionRepository connections = mock(ProviderConnectionRepository.class);
        when(connections.findByIdAndOwnerId(connectionId, ownerId))
                .thenReturn(Optional.of(connection));
        when(connections.findModel(connectionId, "model-test")).thenReturn(Optional.of(model));
        CredentialCipherPort cipher = mock(CredentialCipherPort.class);

        LlmProviderPort adapter = mock(LlmProviderPort.class);
        when(adapter.providerType()).thenReturn(providerType);
        Flow.Publisher<LlmChunk> publisher = subscriber -> {};
        when(adapter.streamChat(any(), any(), any())).thenReturn(publisher);

        ConfiguredLlmChatGateway gateway =
                new ConfiguredLlmChatGateway(connections, cipher, List.of(adapter));
        McpToolDefinition tool = new McpToolDefinition("health_check", "desc", Map.of(), true);

        gateway.stream(
                ownerId,
                connectionId,
                "model-test",
                List.of(new ChatMessage("user", "hola")),
                List.of(tool));

        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(adapter).streamChat(any(), any(), captor.capture());
        return captor.getValue().tools();
    }
}
