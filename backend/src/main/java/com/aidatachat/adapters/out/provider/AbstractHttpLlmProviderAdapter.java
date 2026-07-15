package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ProviderCapabilities;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderDescriptor;
import com.aidatachat.domain.model.ProviderProbeResult;
import java.util.concurrent.Flow;

abstract class AbstractHttpLlmProviderAdapter implements LlmProviderPort {

    protected final ProviderHttpClient http;

    AbstractHttpLlmProviderAdapter(ProviderHttpClient http) {
        this.http = http;
    }

    @Override
    public ProviderDescriptor descriptor() {
        ProviderCapabilityProfile profile = capabilities(ProviderClientConfiguration.empty());
        return new ProviderDescriptor(
                providerType().name().toLowerCase(),
                displayName(),
                providerType().name(),
                IntegrationState.DEGRADED,
                new ProviderCapabilities(
                        supported(profile.chat()),
                        supported(profile.streaming()),
                        supported(profile.toolCalling()),
                        supported(profile.structuredOutput()),
                        supported(profile.vision()),
                        supported(profile.embeddings()),
                        supported(profile.modelDiscovery())),
                false);
    }

    @Override
    public ProviderProbeResult testConnection(
            ProviderClientConfiguration configuration, char[] credential) {
        try {
            String requestId = probe(configuration, credential);
            return ProviderProbeResult.success(requestId);
        } catch (ProviderCommunicationException exception) {
            return ProviderProbeResult.failure(
                    exception.code(),
                    safeMessage(exception.code()),
                    exception.providerRequestId(),
                    exception.retryable());
        }
    }

    @Override
    public Flow.Publisher<LlmChunk> streamChat(LlmChatRequest request) {
        throw new UnsupportedOperationException("Chat streaming belongs to Sprint 3");
    }

    protected abstract String probe(ProviderClientConfiguration configuration, char[] credential);

    protected abstract String displayName();

    protected String bearer(char[] credential) {
        return "Bearer " + new String(credential);
    }

    protected String secret(char[] credential) {
        return new String(credential);
    }

    protected String safeMessage(String code) {
        return switch (code) {
            case "PROVIDER_AUTHENTICATION_FAILED" ->
                    "El proveedor rechazo la credencial configurada.";
            case "PROVIDER_RATE_LIMITED" ->
                    "El proveedor aplico un limite temporal. Intenta mas tarde.";
            case "PROVIDER_TIMEOUT" -> "El proveedor no respondio dentro del tiempo permitido.";
            case "PROVIDER_DESTINATION_BLOCKED" ->
                    "El destino no esta autorizado por la politica del servidor.";
            default -> "No fue posible verificar la conexion con el proveedor.";
        };
    }

    private boolean supported(CapabilityAvailability availability) {
        return availability == CapabilityAvailability.SUPPORTED;
    }
}
