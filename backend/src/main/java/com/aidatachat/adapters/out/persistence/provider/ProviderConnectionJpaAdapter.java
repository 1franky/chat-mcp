package com.aidatachat.adapters.out.persistence.provider;

import com.aidatachat.application.port.out.ProviderConnectionRepository;
import com.aidatachat.domain.model.ProviderConnection;
import com.aidatachat.domain.model.ProviderModel;
import com.aidatachat.domain.model.ProviderModelOrigin;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProviderConnectionJpaAdapter implements ProviderConnectionRepository {

    private final SpringDataProviderConnectionRepository connections;
    private final SpringDataProviderModelRepository models;

    public ProviderConnectionJpaAdapter(
            SpringDataProviderConnectionRepository connections,
            SpringDataProviderModelRepository models) {
        this.connections = connections;
        this.models = models;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderConnection> findAllByOwnerId(UUID ownerId) {
        return connections.findAllByOwnerIdOrderByUpdatedAtDescIdAsc(ownerId).stream()
                .map(ProviderConnectionEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderConnection> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return connections.findByIdAndOwnerId(id, ownerId).map(ProviderConnectionEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOwnerIdAndDisplayName(UUID ownerId, String displayName) {
        return connections.existsByOwnerIdAndDisplayName(ownerId, displayName);
    }

    @Override
    @Transactional
    public ProviderConnection save(ProviderConnection connection) {
        ProviderConnectionEntity entity =
                connections
                        .findById(connection.id())
                        .map(
                                current -> {
                                    current.update(connection);
                                    return current;
                                })
                        .orElseGet(() -> new ProviderConnectionEntity(connection));
        return connections.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public void delete(ProviderConnection connection) {
        connections
                .findByIdAndOwnerId(connection.id(), connection.ownerId())
                .ifPresent(connections::delete);
        connections.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderModel> findModels(UUID providerConnectionId) {
        return models.findAllByProviderConnectionId(providerConnectionId).stream()
                .map(ProviderModelEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderModel> findModel(UUID providerConnectionId, String modelId) {
        return models.findByProviderConnectionIdAndModelId(providerConnectionId, modelId)
                .map(ProviderModelEntity::toDomain);
    }

    @Override
    @Transactional
    public ProviderModel saveModel(ProviderModel model) {
        ProviderModelEntity entity =
                models.findByProviderConnectionIdAndModelId(
                                model.providerConnectionId(), model.modelId())
                        .map(
                                current -> {
                                    current.update(model);
                                    return current;
                                })
                        .orElseGet(() -> new ProviderModelEntity(model));
        return models.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public void replaceConfiguredModel(UUID providerConnectionId, ProviderModel model) {
        models.deleteAllByProviderConnectionIdAndOrigin(
                providerConnectionId, ProviderModelOrigin.CONFIGURED);
        models.flush();
        if (model != null) {
            models.saveAndFlush(new ProviderModelEntity(model));
        }
    }

    @Override
    @Transactional
    public void replaceDiscoveredModels(
            UUID providerConnectionId,
            List<ProviderModel> discoveredModels,
            Instant synchronizedAt) {
        Set<String> preservedIds =
                new HashSet<>(
                        models.findAllByProviderConnectionId(providerConnectionId).stream()
                                .filter(
                                        model ->
                                                model.toDomain().origin()
                                                        != ProviderModelOrigin.DISCOVERED)
                                .map(model -> model.toDomain().modelId())
                                .toList());
        models.deleteAllByProviderConnectionIdAndOrigin(
                providerConnectionId, ProviderModelOrigin.DISCOVERED);
        models.flush();
        List<ProviderModelEntity> entities =
                discoveredModels.stream()
                        .filter(model -> !preservedIds.contains(model.modelId()))
                        .map(ProviderModelEntity::new)
                        .toList();
        models.saveAllAndFlush(entities);
    }
}
