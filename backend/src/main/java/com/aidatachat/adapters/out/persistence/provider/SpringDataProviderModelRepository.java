package com.aidatachat.adapters.out.persistence.provider;

import com.aidatachat.domain.model.ProviderModelOrigin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProviderModelRepository extends JpaRepository<ProviderModelEntity, UUID> {

    List<ProviderModelEntity> findAllByProviderConnectionId(UUID providerConnectionId);

    Optional<ProviderModelEntity> findByProviderConnectionIdAndModelId(
            UUID providerConnectionId, String modelId);

    void deleteAllByProviderConnectionIdAndOrigin(
            UUID providerConnectionId, ProviderModelOrigin origin);
}
