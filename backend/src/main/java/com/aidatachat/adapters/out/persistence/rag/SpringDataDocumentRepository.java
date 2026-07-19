package com.aidatachat.adapters.out.persistence.rag;

import com.aidatachat.domain.model.DocumentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataDocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<DocumentEntity> findAllByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);

    Optional<DocumentEntity> findByOwnerIdAndContentHash(UUID ownerId, String contentHash);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    @Query(
            value =
                    """
                    select document from DocumentEntity document
                    where document.ownerId = :ownerId
                      and (:status is null or document.status = :status)
                    """,
            countQuery =
                    """
                    select count(document) from DocumentEntity document
                    where document.ownerId = :ownerId
                      and (:status is null or document.status = :status)
                    """)
    Page<DocumentEntity> findOwned(UUID ownerId, DocumentStatus status, Pageable pageable);
}
