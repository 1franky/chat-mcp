package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.domain.model.MessageDocumentRelation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code rag.message_document}: which documents were in scope for a message ({@code
 * relation=SELECTED}, {@code chunkId} null) and which chunks were actually surfaced as a citation
 * ({@code relation=CITED}, {@code chunkId} not null). Deliberately holds no chunk content — {@code
 * document_chunk} lives outside JPA (see {@code PgVectorSearchAdapter}), so citations are hydrated
 * in the application layer via {@code VectorSearchPort.findByIds}.
 */
@Entity
@Table(name = "message_document", schema = "rag")
class MessageDocumentEntity {

    @Id private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageDocumentRelation relation;

    @Column private Double score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageDocumentEntity() {}

    MessageDocumentEntity(
            UUID id,
            UUID messageId,
            UUID documentId,
            UUID chunkId,
            MessageDocumentRelation relation,
            Double score,
            Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.relation = relation;
        this.score = score;
        this.createdAt = createdAt;
    }

    UUID getMessageId() {
        return messageId;
    }

    UUID getDocumentId() {
        return documentId;
    }

    UUID getChunkId() {
        return chunkId;
    }

    MessageDocumentRelation getRelation() {
        return relation;
    }

    Double getScore() {
        return score;
    }
}
