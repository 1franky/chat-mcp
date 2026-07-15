package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderConnectionState;
import com.aidatachat.domain.model.ProviderModelOrigin;
import com.aidatachat.domain.model.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProviderManagementUseCase {

    List<ProviderConnectionView> listConnections(UUID ownerId);

    ProviderConnectionView createConnection(UUID ownerId, SaveProviderCommand command);

    ProviderConnectionView updateConnection(
            UUID ownerId, UUID connectionId, SaveProviderCommand command);

    void deleteConnection(UUID ownerId, UUID connectionId, String remoteAddress);

    ProviderTestView testConnection(UUID ownerId, UUID connectionId, String remoteAddress);

    List<ProviderModelView> synchronizeModels(
            UUID ownerId, UUID connectionId, String remoteAddress);

    List<ProviderModelView> listModels(UUID ownerId, UUID connectionId);

    ProviderModelView addManualModel(
            UUID ownerId, UUID connectionId, String modelId, String remoteAddress);

    ProviderConnectionView selectDefaultModel(UUID ownerId, UUID connectionId, String modelId);

    record SaveProviderCommand(
            String displayName,
            ProviderType providerType,
            String apiKey,
            String baseUrl,
            String region,
            String modelsPath,
            String responsesPath,
            String chatCompletionsPath,
            String configuredModelId,
            String remoteAddress) {}

    record ProviderConnectionView(
            UUID id,
            String displayName,
            ProviderType providerType,
            String baseUrl,
            String region,
            String modelsPath,
            String responsesPath,
            String chatCompletionsPath,
            String configuredModelId,
            String defaultModelId,
            String credentialMasked,
            ProviderConnectionState state,
            String lastErrorCode,
            Instant lastTestedAt,
            Instant lastModelsSyncedAt,
            ProviderCapabilityProfile capabilities,
            Instant createdAt,
            Instant updatedAt) {}

    record ProviderModelView(
            UUID id,
            String modelId,
            String displayName,
            ProviderModelOrigin origin,
            ProviderCapabilityProfile capabilities,
            Instant discoveredAt,
            Instant lastValidatedAt) {}

    record ProviderTestView(
            boolean success,
            String code,
            String message,
            String providerRequestId,
            boolean retryable,
            Instant testedAt) {}
}
