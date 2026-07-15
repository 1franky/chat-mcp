package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderDescriptor;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

public interface LlmProviderPort {

    ProviderType providerType();

    ProviderDescriptor descriptor();

    ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration);

    ProviderProbeResult testConnection(
            ProviderClientConfiguration configuration, char[] credential);

    List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential);

    default ProviderProbeResult validateModel(
            ProviderClientConfiguration configuration, char[] credential, String modelId) {
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

    Flow.Publisher<LlmChunk> streamChat(LlmChatRequest request);

    default Flow.Publisher<LlmChunk> streamChat(
            ProviderClientConfiguration configuration, char[] credential, LlmChatRequest request) {
        return streamChat(request);
    }

    record ProviderClientConfiguration(
            String baseUrl,
            String region,
            String modelsPath,
            String responsesPath,
            String chatCompletionsPath,
            String configuredModelId) {

        public ProviderClientConfiguration {
            if (baseUrl != null && baseUrl.isBlank()) {
                baseUrl = null;
            }
            if (region != null && region.isBlank()) {
                region = null;
            }
            if (modelsPath != null && modelsPath.isBlank()) {
                modelsPath = null;
            }
            if (responsesPath != null && responsesPath.isBlank()) {
                responsesPath = null;
            }
            if (chatCompletionsPath != null && chatCompletionsPath.isBlank()) {
                chatCompletionsPath = null;
            }
            if (configuredModelId != null && configuredModelId.isBlank()) {
                configuredModelId = null;
            }
        }

        public static ProviderClientConfiguration empty() {
            return new ProviderClientConfiguration(null, null, null, null, null, null);
        }

        public String requireConfiguredModelId() {
            return Objects.requireNonNull(
                    configuredModelId, "A configured model ID is required for this provider");
        }
    }
}
