package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class OpenAiCompatibleProviderAdapter extends AbstractHttpLlmProviderAdapter {

    private final ProviderDestinationPolicy destinationPolicy;

    public OpenAiCompatibleProviderAdapter(
            ProviderHttpClient http, ProviderDestinationPolicy destinationPolicy) {
        super(http);
        this.destinationPolicy = destinationPolicy;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration) {
        return new ProviderCapabilityProfile(
                CapabilityAvailability.SUPPORTED,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                configuration.modelsPath() == null
                        ? CapabilityAvailability.UNSUPPORTED
                        : CapabilityAvailability.SUPPORTED);
    }

    @Override
    public Flow.Publisher<LlmChunk> streamChat(
            ProviderClientConfiguration configuration, char[] credential, LlmChatRequest request) {
        String token = secret(credential);
        if (configuration.responsesPath() != null) {
            return ProviderStreamingSupport.map(
                    http.postSse(
                            customUri(configuration, configuration.responsesPath()),
                            headers -> headers.setBearerAuth(token),
                            ProviderStreamingSupport.responsesBody(request)),
                    ProviderStreamingSupport::parseOpenAiResponses);
        }
        String chatPath =
                configuration.chatCompletionsPath() == null
                        ? "/chat/completions"
                        : configuration.chatCompletionsPath();
        return ProviderStreamingSupport.map(
                http.postSse(
                        customUri(configuration, chatPath),
                        headers -> headers.setBearerAuth(token),
                        ProviderStreamingSupport.chatCompletionsBody(request, 0)),
                ProviderStreamingSupport::parseChatCompletions);
    }

    @Override
    public List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential) {
        if (configuration.modelsPath() == null) {
            String modelId = configuration.requireConfiguredModelId();
            return List.of(
                    new DiscoveredProviderModel(
                            modelId, modelId, ProviderCapabilityProfile.unknown()));
        }
        URI uri = customUri(configuration, configuration.modelsPath());
        ProviderHttpClient.JsonResponse response =
                http.get(uri, headers -> headers.setBearerAuth(secret(credential)));
        JsonNode data = response.body().path("data");
        if (!data.isArray()) {
            throw new ProviderCommunicationException(
                    "PROVIDER_INVALID_RESPONSE", response.requestId(), false, null);
        }
        List<DiscoveredProviderModel> models = new ArrayList<>();
        for (JsonNode item : data) {
            String id = item.path("id").asText("");
            if (!id.isBlank()) {
                models.add(
                        new DiscoveredProviderModel(id, id, ProviderCapabilityProfile.unknown()));
            }
        }
        return models;
    }

    @Override
    public ProviderProbeResult validateModel(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
        if (configuration.modelsPath() != null) {
            boolean found =
                    discoverModels(configuration, credential).stream()
                            .anyMatch(model -> model.modelId().equals(modelId));
            return found
                    ? ProviderProbeResult.success(null)
                    : ProviderProbeResult.failure(
                            "MODEL_NOT_AVAILABLE",
                            "El modelo no aparece entre los modelos disponibles.",
                            null,
                            false);
        }
        return ProviderProbeResult.success(invoke(configuration, credential, modelId));
    }

    @Override
    protected String probe(ProviderClientConfiguration configuration, char[] credential) {
        if (configuration.modelsPath() != null) {
            URI uri = customUri(configuration, configuration.modelsPath());
            return http.get(uri, headers -> headers.setBearerAuth(secret(credential))).requestId();
        }
        return invoke(configuration, credential, configuration.requireConfiguredModelId());
    }

    @Override
    protected String displayName() {
        return "OpenAI compatible";
    }

    private String invoke(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
        String responsesPath = configuration.responsesPath();
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", modelId);
        URI uri;
        if (responsesPath != null) {
            body.put("input", "ping");
            body.put("max_output_tokens", 1);
            uri = customUri(configuration, responsesPath);
        } else {
            body.put("max_tokens", 1);
            body.putArray("messages").addObject().put("role", "user").put("content", "ping");
            String chatPath =
                    configuration.chatCompletionsPath() == null
                            ? "/chat/completions"
                            : configuration.chatCompletionsPath();
            uri = customUri(configuration, chatPath);
        }
        return http.post(uri, headers -> headers.setBearerAuth(secret(credential)), body)
                .requestId();
    }

    private URI customUri(ProviderClientConfiguration configuration, String path) {
        URI base = URI.create(configuration.baseUrl());
        destinationPolicy.validateCustomDestination(base);
        return ProviderUris.append(configuration.baseUrl(), path);
    }
}
