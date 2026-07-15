package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.ProviderConnection;
import com.aidatachat.domain.model.ProviderModel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderConnectionRepository {

    List<ProviderConnection> findAllByOwnerId(UUID ownerId);

    Optional<ProviderConnection> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndDisplayName(UUID ownerId, String displayName);

    ProviderConnection save(ProviderConnection connection);

    void delete(ProviderConnection connection);

    List<ProviderModel> findModels(UUID providerConnectionId);

    Optional<ProviderModel> findModel(UUID providerConnectionId, String modelId);

    ProviderModel saveModel(ProviderModel model);

    void replaceConfiguredModel(UUID providerConnectionId, ProviderModel model);

    void replaceDiscoveredModels(
            UUID providerConnectionId, List<ProviderModel> models, Instant synchronizedAt);
}
