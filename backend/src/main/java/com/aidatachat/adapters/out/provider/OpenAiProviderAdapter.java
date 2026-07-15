package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

public final class OpenAiProviderAdapter extends AbstractHttpLlmProviderAdapter {

    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final ProviderCapabilityProfile CAPABILITIES =
            new ProviderCapabilityProfile(
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED);

    private final String baseUrl;

    public OpenAiProviderAdapter(ProviderHttpClient http) {
        this(http, DEFAULT_BASE_URL);
    }

    OpenAiProviderAdapter(ProviderHttpClient http, String baseUrl) {
        super(http);
        this.baseUrl = baseUrl;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.OPENAI;
    }

    @Override
    public ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration) {
        return CAPABILITIES;
    }

    @Override
    public List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential) {
        return listModels(ProviderUris.append(baseUrl, "/models"), credential).models();
    }

    @Override
    public Flow.Publisher<LlmChunk> streamChat(
            ProviderClientConfiguration configuration, char[] credential, LlmChatRequest request) {
        String token = secret(credential);
        return ProviderStreamingSupport.map(
                http.postSse(
                        ProviderUris.append(baseUrl, "/responses"),
                        headers -> headers.setBearerAuth(token),
                        ProviderStreamingSupport.responsesBody(request)),
                ProviderStreamingSupport::parseOpenAiResponses);
    }

    @Override
    public ProviderProbeResult validateModel(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
        URI uri =
                ProviderUris.append(
                        baseUrl,
                        "/models/" + UriUtils.encodePathSegment(modelId, StandardCharsets.UTF_8));
        ProviderHttpClient.JsonResponse response =
                http.get(uri, headers -> headers.setBearerAuth(secret(credential)));
        return ProviderProbeResult.success(response.requestId());
    }

    @Override
    protected String probe(ProviderClientConfiguration configuration, char[] credential) {
        return listModels(ProviderUris.append(baseUrl, "/models"), credential).requestId();
    }

    @Override
    protected String displayName() {
        return "OpenAI";
    }

    private ModelPage listModels(URI uri, char[] credential) {
        ProviderHttpClient.JsonResponse response =
                http.get(uri, headers -> headers.setBearerAuth(secret(credential)));
        List<DiscoveredProviderModel> models = new ArrayList<>();
        JsonNode data = response.body().path("data");
        if (!data.isArray()) {
            throw new com.aidatachat.application.exception.ProviderCommunicationException(
                    "PROVIDER_INVALID_RESPONSE", response.requestId(), false, null);
        }
        for (JsonNode item : data) {
            String id = item.path("id").asText("");
            if (!id.isBlank()) {
                models.add(
                        new DiscoveredProviderModel(id, id, ProviderCapabilityProfile.unknown()));
            }
        }
        return new ModelPage(models, response.requestId());
    }

    private record ModelPage(List<DiscoveredProviderModel> models, String requestId) {}
}
