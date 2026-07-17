package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.util.List;
import java.util.concurrent.Flow;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class MiniMaxProviderAdapter extends AbstractHttpLlmProviderAdapter {

    static final String DEFAULT_BASE_URL = "https://api.minimax.io/v1";
    private static final int DEFAULT_MAX_TOKENS = 4_096;
    private static final ProviderCapabilityProfile CAPABILITIES =
            new ProviderCapabilityProfile(
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.UNKNOWN,
                    CapabilityAvailability.UNKNOWN,
                    CapabilityAvailability.UNKNOWN,
                    CapabilityAvailability.UNKNOWN,
                    CapabilityAvailability.UNSUPPORTED);

    private final String baseUrl;

    public MiniMaxProviderAdapter(ProviderHttpClient http) {
        this(http, DEFAULT_BASE_URL);
    }

    MiniMaxProviderAdapter(ProviderHttpClient http, String baseUrl) {
        super(http);
        this.baseUrl = baseUrl;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.MINIMAX;
    }

    @Override
    public ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration) {
        return CAPABILITIES;
    }

    @Override
    public Flow.Publisher<LlmChunk> streamChat(
            ProviderClientConfiguration configuration, char[] credential, LlmChatRequest request) {
        String token = secret(credential);
        return ProviderStreamingSupport.map(
                http.postSse(
                        ProviderUris.append(baseUrl, "/chat/completions"),
                        headers -> headers.setBearerAuth(token),
                        ProviderStreamingSupport.chatCompletionsBody(request, DEFAULT_MAX_TOKENS)),
                ProviderStreamingSupport::parseChatCompletions);
    }

    @Override
    public List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential) {
        return List.of();
    }

    @Override
    public ProviderProbeResult validateModel(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
        String requestId = invoke(configuration, credential, modelId);
        return ProviderProbeResult.success(requestId);
    }

    @Override
    protected String probe(ProviderClientConfiguration configuration, char[] credential) {
        return invoke(configuration, credential, configuration.requireConfiguredModelId());
    }

    @Override
    protected String displayName() {
        return "MiniMax";
    }

    private String invoke(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", modelId);
        body.put("max_tokens", 1);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", "ping");
        ProviderHttpClient.JsonResponse response =
                http.post(
                        ProviderUris.append(baseUrl, "/chat/completions"),
                        headers -> headers.setBearerAuth(secret(credential)),
                        body);
        return response.requestId();
    }
}
