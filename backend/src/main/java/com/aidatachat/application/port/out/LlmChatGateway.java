package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.ProviderType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;

public interface LlmChatGateway {

    ProviderSelection validateSelection(UUID ownerId, UUID providerConnectionId, String modelId);

    ChatStream stream(
            UUID ownerId,
            UUID providerConnectionId,
            String modelId,
            List<ChatMessage> messages,
            List<McpToolDefinition> tools);

    record ChatStream(
            ProviderType providerType, String modelId, Flow.Publisher<LlmChunk> publisher) {}

    record ProviderSelection(ProviderType providerType, String modelId) {}
}
