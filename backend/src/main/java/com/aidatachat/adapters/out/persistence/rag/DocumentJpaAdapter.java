package com.aidatachat.adapters.out.persistence.rag;

import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

public class DocumentJpaAdapter implements DocumentRepository {

    private final SpringDataDocumentRepository documents;

    public DocumentJpaAdapter(SpringDataDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdAndOwnerId(UUID documentId, UUID ownerId) {
        return documents.existsByIdAndOwnerId(documentId, ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findByIdAndOwnerId(UUID documentId, UUID ownerId) {
        return documents.findByIdAndOwnerId(documentId, ownerId).map(DocumentEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentPage findAllByOwnerId(
            UUID ownerId, DocumentStatus statusFilter, int page, int size) {
        Page<DocumentEntity> result =
                documents.findOwned(
                        ownerId,
                        statusFilter,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return new DocumentPage(
                result.getContent().stream().map(DocumentEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional
    public Document save(Document document) {
        DocumentEntity entity =
                documents
                        .findByIdAndOwnerId(document.id(), document.ownerId())
                        .map(
                                current -> {
                                    current.update(document);
                                    return current;
                                })
                        .orElseGet(() -> new DocumentEntity(document));
        return documents.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public void deleteByIdAndOwnerId(UUID documentId, UUID ownerId) {
        documents.findByIdAndOwnerId(documentId, ownerId).ifPresent(documents::delete);
        documents.flush();
    }
}
