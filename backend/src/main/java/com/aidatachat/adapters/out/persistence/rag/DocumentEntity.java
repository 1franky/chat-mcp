package com.aidatachat.adapters.out.persistence.rag;

import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document", schema = "rag")
class DocumentEntity {

    @Id private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "embedding_model_id", length = 255)
    private String embeddingModelId;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {}

    DocumentEntity(Document document) {
        this.id = document.id();
        this.ownerId = document.ownerId();
        this.createdAt = document.createdAt();
        update(document);
    }

    void update(Document document) {
        this.originalFilename = document.originalFilename();
        this.storageKey = document.storageKey();
        this.mimeType = document.mimeType();
        this.byteSize = document.byteSize();
        this.contentHash = document.contentHash();
        this.status = document.status();
        this.failureReason = document.failureReason();
        this.embeddingModelId = document.embeddingModelId();
        this.embeddingDimension = document.embeddingDimension();
        this.chunkCount = document.chunkCount();
        this.updatedAt = document.updatedAt();
    }

    Document toDomain() {
        return new Document(
                id,
                ownerId,
                originalFilename,
                storageKey,
                mimeType,
                byteSize,
                contentHash,
                status,
                failureReason,
                embeddingModelId,
                embeddingDimension,
                chunkCount,
                version,
                createdAt,
                updatedAt);
    }
}
