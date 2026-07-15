package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

public final class AnthropicProviderAdapter extends AbstractHttpLlmProviderAdapter {

    static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";
    private static final ProviderCapabilityProfile CAPABILITIES =
            new ProviderCapabilityProfile(
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.UNSUPPORTED,
                    CapabilityAvailability.SUPPORTED);

    private final String baseUrl;

    public AnthropicProviderAdapter(ProviderHttpClient http) {
        this(http, DEFAULT_BASE_URL);
    }

    AnthropicProviderAdapter(ProviderHttpClient http, String baseUrl) {
        super(http);
        this.baseUrl = baseUrl;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration) {
        return CAPABILITIES;
    }

    @Override
    public List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential) {
        return listModels(credential).models();
    }

    @Override
    public ProviderProbeResult validateModel(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
        URI uri =
                ProviderUris.append(
                        baseUrl,
                        "/models/" + UriUtils.encodePathSegment(modelId, StandardCharsets.UTF_8));
        ProviderHttpClient.JsonResponse response = http.get(uri, headers(credential));
        return ProviderProbeResult.success(response.requestId());
    }

    @Override
    protected String probe(ProviderClientConfiguration configuration, char[] credential) {
        return listModels(credential).requestId();
    }

    @Override
    protected String displayName() {
        return "Anthropic";
    }

    private ModelPage listModels(char[] credential) {
        ProviderHttpClient.JsonResponse response =
                http.get(ProviderUris.append(baseUrl, "/models?limit=1000"), headers(credential));
        JsonNode data = response.body().path("data");
        if (!data.isArray()) {
            throw new ProviderCommunicationException(
                    "PROVIDER_INVALID_RESPONSE", response.requestId(), false, null);
        }
        List<DiscoveredProviderModel> models = new ArrayList<>();
        for (JsonNode item : data) {
            String id = item.path("id").asText("");
            if (!id.isBlank()) {
                String displayName = item.path("display_name").asText(id);
                models.add(
                        new DiscoveredProviderModel(
                                id, displayName, ProviderCapabilityProfile.unknown()));
            }
        }
        return new ModelPage(models, response.requestId());
    }

    private java.util.function.Consumer<HttpHeaders> headers(char[] credential) {
        return headers -> {
            headers.set("x-api-key", secret(credential));
            headers.set("anthropic-version", API_VERSION);
        };
    }

    private record ModelPage(List<DiscoveredProviderModel> models, String requestId) {}
}
