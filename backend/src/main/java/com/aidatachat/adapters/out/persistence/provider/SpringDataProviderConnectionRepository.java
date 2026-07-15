package com.aidatachat.adapters.out.persistence.provider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataProviderConnectionRepository
        extends JpaRepository<ProviderConnectionEntity, UUID> {

    List<ProviderConnectionEntity> findAllByOwnerIdOrderByUpdatedAtDescIdAsc(UUID ownerId);

    Optional<ProviderConnectionEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query(
            """
            select (count(connection) > 0) from ProviderConnectionEntity connection
            where connection.ownerId = :ownerId
              and lower(connection.displayName) = lower(:displayName)
            """)
    boolean existsByOwnerIdAndDisplayName(UUID ownerId, String displayName);
}
