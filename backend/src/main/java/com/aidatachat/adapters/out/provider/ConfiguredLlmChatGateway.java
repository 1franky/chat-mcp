package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ChatConflictException;
import com.aidatachat.application.exception.ProviderNotFoundException;
import com.aidatachat.application.port.out.CredentialCipherPort;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.ProviderConnectionRepository;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.EncryptedProviderCredential;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.ProviderConnection;
import com.aidatachat.domain.model.ProviderModel;
import com.aidatachat.domain.model.ProviderType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ConfiguredLlmChatGateway implements LlmChatGateway {

    private static final Set<ProviderType> TOOL_CALLING_PROVIDERS =
            EnumSet.of(ProviderType.OPENAI, ProviderType.ANTHROPIC);

    private final ProviderConnectionRepository connections;
    private final CredentialCipherPort cipher;
    private final Map<ProviderType, LlmProviderPort> adapters;

    public ConfiguredLlmChatGateway(
            ProviderConnectionRepository connections,
            CredentialCipherPort cipher,
            List<LlmProviderPort> adapters) {
        this.connections = Objects.requireNonNull(connections);
        this.cipher = Objects.requireNonNull(cipher);
        this.adapters = new EnumMap<>(ProviderType.class);
        for (LlmProviderPort adapter : adapters) {
            this.adapters.put(adapter.providerType(), adapter);
        }
    }

    @Override
    public ProviderSelection validateSelection(
            UUID ownerId, UUID providerConnectionId, String modelId) {
        ResolvedSelection resolved = resolve(ownerId, providerConnectionId, modelId);
        return new ProviderSelection(
                resolved.connection().providerType(), resolved.model().modelId());
    }

    @Override
    public ChatStream stream(
            UUID ownerId,
            UUID providerConnectionId,
            String modelId,
            List<ChatMessage> messages,
            List<McpToolDefinition> tools) {
        ResolvedSelection resolved = resolve(ownerId, providerConnectionId, modelId);
        ProviderConnection connection = resolved.connection();
        ProviderModel model = resolved.model();
        LlmProviderPort adapter = adapters.get(connection.providerType());
        if (adapter == null) {
            throw new ChatConflictException("No adapter is configured for the selected provider");
        }
        List<McpToolDefinition> offeredTools =
                TOOL_CALLING_PROVIDERS.contains(connection.providerType()) ? tools : List.of();

        char[] credential = decrypt(connection.credential());
        try {
            return new ChatStream(
                    connection.providerType(),
                    model.modelId(),
                    adapter.streamChat(
                            configuration(connection),
                            credential,
                            new LlmChatRequest(model.modelId(), messages, offeredTools)));
        } finally {
            Arrays.fill(credential, '\0');
        }
    }

    private ResolvedSelection resolve(UUID ownerId, UUID providerConnectionId, String modelId) {
        ProviderConnection connection =
                connections
                        .findByIdAndOwnerId(providerConnectionId, ownerId)
                        .orElseThrow(ProviderNotFoundException::new);
        ProviderModel model =
                connections
                        .findModel(connection.id(), modelId)
                        .orElseThrow(
                                () ->
                                        new ChatConflictException(
                                                "The selected model is unavailable"));
        if (model.capabilities().chat() == CapabilityAvailability.UNSUPPORTED
                || model.capabilities().streaming() == CapabilityAvailability.UNSUPPORTED) {
            throw new ChatConflictException("The selected model does not support streaming chat");
        }
        return new ResolvedSelection(connection, model);
    }

    private char[] decrypt(EncryptedProviderCredential credential) {
        if (credential == null) {
            return new char[0];
        }
        return cipher.decrypt(
                new CredentialCipherPort.EncryptedCredential(
                        credential.ciphertext(), credential.nonce(), credential.keyVersion()));
    }

    private LlmProviderPort.ProviderClientConfiguration configuration(
            ProviderConnection connection) {
        return new LlmProviderPort.ProviderClientConfiguration(
                connection.baseUrl(),
                connection.region(),
                connection.modelsPath(),
                connection.responsesPath(),
                connection.chatCompletionsPath(),
                connection.configuredModelId());
    }

    private record ResolvedSelection(ProviderConnection connection, ProviderModel model) {}
}
