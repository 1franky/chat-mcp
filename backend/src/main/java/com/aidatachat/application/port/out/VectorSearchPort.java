package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.DocumentChunk;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VectorSearchPort {

    /**
     * Replaces the full set of chunks for a document in one atomic operation (delete-then-insert):
     * the initial write of content + embedding together, produced by chunking/embedding. Idempotent
     * — safe to call again on reprocessing.
     */
    void replaceChunks(UUID ownerId, UUID documentId, List<ChunkRecord> chunks);

    /**
     * Recomputes the embedding of chunks that already exist (future reindex-with-new-model use
     * case); never creates rows. Not called by any use case yet.
     */
    void index(UUID ownerId, UUID documentId, List<VectorRecord> vectors);

    /**
     * Ranks chunks by cosine similarity to {@code query}, restricted to {@code documentIds} —
     * never searches beyond the caller-supplied scope, even within the same owner.
     */
    List<VectorMatch> search(UUID ownerId, Collection<UUID> documentIds, float[] query, int topK);

    /** Resolves full chunk content (for prompt context / citations) by id, scoped to the owner. */
    List<DocumentChunk> findByIds(UUID ownerId, Collection<UUID> chunkIds);

    void deleteByDocument(UUID ownerId, UUID documentId);

    record ChunkRecord(
            UUID chunkId,
            int chunkIndex,
            String content,
            Integer pageNumber,
            String sectionLabel,
            String embeddingModelId,
            float[] embedding,
            Instant createdAt) {}

    record VectorRecord(UUID chunkId, float[] embedding) {}

    record VectorMatch(UUID chunkId, double score) {}
}
