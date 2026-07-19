package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.domain.model.DocumentChunk;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeVectorSearchAdapter implements VectorSearchPort {

    private record Entry(UUID ownerId, UUID documentId, ChunkRecord chunk) {}

    private final ConcurrentHashMap<UUID, Entry> chunks = new ConcurrentHashMap<>();

    @Override
    public void replaceChunks(UUID ownerId, UUID documentId, List<ChunkRecord> chunks) {
        this.chunks
                .entrySet()
                .removeIf(
                        entry ->
                                entry.getValue().ownerId().equals(ownerId)
                                        && entry.getValue().documentId().equals(documentId));
        for (ChunkRecord chunk : chunks) {
            this.chunks.put(chunk.chunkId(), new Entry(ownerId, documentId, chunk));
        }
    }

    @Override
    public void index(UUID ownerId, UUID documentId, List<VectorRecord> vectors) {
        for (VectorRecord vector : vectors) {
            Entry existing = chunks.get(vector.chunkId());
            ChunkRecord updated =
                    existing == null
                            ? new ChunkRecord(
                                    vector.chunkId(),
                                    0,
                                    "unknown",
                                    null,
                                    null,
                                    "unknown",
                                    vector.embedding(),
                                    java.time.Instant.EPOCH)
                            : withEmbedding(existing.chunk(), vector.embedding());
            chunks.put(vector.chunkId(), new Entry(ownerId, documentId, updated));
        }
    }

    @Override
    public List<VectorMatch> search(
            UUID ownerId, Collection<UUID> documentIds, float[] query, int topK) {
        return chunks.entrySet().stream()
                .filter(entry -> entry.getValue().ownerId().equals(ownerId))
                .filter(entry -> documentIds.contains(entry.getValue().documentId()))
                .map(
                        entry ->
                                new VectorMatch(
                                        entry.getKey(),
                                        cosineSimilarity(
                                                query, entry.getValue().chunk().embedding())))
                .sorted(Comparator.comparingDouble(VectorMatch::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public List<DocumentChunk> findByIds(UUID ownerId, Collection<UUID> chunkIds) {
        return chunks.entrySet().stream()
                .filter(entry -> entry.getValue().ownerId().equals(ownerId))
                .filter(entry -> chunkIds.contains(entry.getKey()))
                .map(
                        entry -> {
                            ChunkRecord chunk = entry.getValue().chunk();
                            return new DocumentChunk(
                                    chunk.chunkId(),
                                    entry.getValue().documentId(),
                                    entry.getValue().ownerId(),
                                    chunk.chunkIndex(),
                                    chunk.content(),
                                    chunk.pageNumber(),
                                    chunk.sectionLabel(),
                                    chunk.embeddingModelId(),
                                    chunk.embedding(),
                                    chunk.createdAt());
                        })
                .toList();
    }

    @Override
    public void deleteByDocument(UUID ownerId, UUID documentId) {
        chunks.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue().ownerId().equals(ownerId)
                                        && entry.getValue().documentId().equals(documentId));
    }

    private ChunkRecord withEmbedding(ChunkRecord chunk, float[] embedding) {
        return new ChunkRecord(
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.pageNumber(),
                chunk.sectionLabel(),
                chunk.embeddingModelId(),
                embedding,
                chunk.createdAt());
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
