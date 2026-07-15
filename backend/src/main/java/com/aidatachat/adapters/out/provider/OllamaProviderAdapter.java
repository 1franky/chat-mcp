package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class OllamaProviderAdapter extends AbstractHttpLlmProviderAdapter {

    private static final ProviderCapabilityProfile CAPABILITIES =
            new ProviderCapabilityProfile(
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.UNKNOWN,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.UNKNOWN,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED);

    private final ProviderDestinationPolicy destinationPolicy;

    public OllamaProviderAdapter(
            ProviderHttpClient http, ProviderDestinationPolicy destinationPolicy) {
        super(http);
        this.destinationPolicy = destinationPolicy;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.OLLAMA;
    }

    @Override
    public ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration) {
        return CAPABILITIES;
    }

    @Override
    public List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential) {
        ProviderHttpClient.JsonResponse response = http.get(tagsUri(configuration), headers -> {});
        JsonNode data = response.body().path("models");
        if (!data.isArray()) {
            throw new ProviderCommunicationException(
                    "PROVIDER_INVALID_RESPONSE", response.requestId(), false, null);
        }
        List<DiscoveredProviderModel> models = new ArrayList<>();
        for (JsonNode item : data) {
            String id = item.path("model").asText(item.path("name").asText(""));
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
        boolean found =
                discoverModels(configuration, credential).stream()
                        .anyMatch(model -> model.modelId().equals(modelId));
        return found
                ? ProviderProbeResult.success(null)
                : ProviderProbeResult.failure(
                        "MODEL_NOT_AVAILABLE",
                        "El modelo no aparece entre los modelos instalados.",
                        null,
                        false);
    }

    @Override
    protected String probe(ProviderClientConfiguration configuration, char[] credential) {
        return http.get(tagsUri(configuration), headers -> {}).requestId();
    }

    @Override
    protected String displayName() {
        return "Ollama";
    }

    private URI tagsUri(ProviderClientConfiguration configuration) {
        URI base = URI.create(configuration.baseUrl());
        destinationPolicy.validateCustomDestination(base);
        return ProviderUris.append(configuration.baseUrl(), "/api/tags");
    }
}
