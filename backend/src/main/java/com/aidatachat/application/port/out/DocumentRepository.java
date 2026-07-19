package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    boolean existsByIdAndOwnerId(UUID documentId, UUID ownerId);

    Optional<Document> findByIdAndOwnerId(UUID documentId, UUID ownerId);

    List<Document> findAllByIdsAndOwnerId(Collection<UUID> documentIds, UUID ownerId);

    Optional<Document> findByOwnerIdAndContentHash(UUID ownerId, String contentHash);

    DocumentPage findAllByOwnerId(UUID ownerId, DocumentStatus statusFilter, int page, int size);

    Document save(Document document);

    void deleteByIdAndOwnerId(UUID documentId, UUID ownerId);

    record DocumentPage(
            List<Document> items, int page, int size, long totalElements, int totalPages) {

        public DocumentPage {
            items = List.copyOf(items);
        }
    }
}
