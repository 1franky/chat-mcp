package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeDocumentRepository implements DocumentRepository {

    private final ConcurrentHashMap<UUID, Document> documents = new ConcurrentHashMap<>();

    @Override
    public boolean existsByIdAndOwnerId(UUID documentId, UUID ownerId) {
        return findByIdAndOwnerId(documentId, ownerId).isPresent();
    }

    @Override
    public Optional<Document> findByIdAndOwnerId(UUID documentId, UUID ownerId) {
        return Optional.ofNullable(documents.get(documentId))
                .filter(document -> document.ownerId().equals(ownerId));
    }

    @Override
    public List<Document> findAllByIdsAndOwnerId(Collection<UUID> documentIds, UUID ownerId) {
        return documents.values().stream()
                .filter(document -> document.ownerId().equals(ownerId))
                .filter(document -> documentIds.contains(document.id()))
                .toList();
    }

    @Override
    public Optional<Document> findByOwnerIdAndContentHash(UUID ownerId, String contentHash) {
        return documents.values().stream()
                .filter(document -> document.ownerId().equals(ownerId))
                .filter(document -> document.contentHash().equals(contentHash))
                .findFirst();
    }

    @Override
    public DocumentPage findAllByOwnerId(
            UUID ownerId, DocumentStatus statusFilter, int page, int size) {
        List<Document> matches =
                documents.values().stream()
                        .filter(document -> document.ownerId().equals(ownerId))
                        .filter(
                                document ->
                                        statusFilter == null || document.status() == statusFilter)
                        .sorted(
                                Comparator.comparing(Document::updatedAt)
                                        .reversed()
                                        .thenComparing(
                                                Comparator.comparing(Document::id).reversed()))
                        .toList();
        int totalElements = matches.size();
        int totalPages = (int) Math.ceil(totalElements / (double) size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        return new DocumentPage(
                matches.subList(fromIndex, toIndex), page, size, totalElements, totalPages);
    }

    @Override
    public Document save(Document document) {
        documents.put(document.id(), document);
        return document;
    }

    @Override
    public void deleteByIdAndOwnerId(UUID documentId, UUID ownerId) {
        documents.computeIfPresent(
                documentId, (id, existing) -> existing.ownerId().equals(ownerId) ? null : existing);
    }
}
