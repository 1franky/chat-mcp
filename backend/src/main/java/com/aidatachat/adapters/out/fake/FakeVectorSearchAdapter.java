package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.port.out.VectorSearchPort;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeVectorSearchAdapter implements VectorSearchPort {

    private record Entry(UUID ownerId, UUID documentId, float[] embedding) {}

    private final ConcurrentHashMap<UUID, Entry> chunks = new ConcurrentHashMap<>();

    @Override
    public void index(UUID ownerId, UUID documentId, List<VectorRecord> vectors) {
        for (VectorRecord vector : vectors) {
            chunks.put(vector.chunkId(), new Entry(ownerId, documentId, vector.embedding()));
        }
    }

    @Override
    public List<VectorMatch> search(UUID ownerId, float[] query, int topK) {
        return chunks.entrySet().stream()
                .filter(entry -> entry.getValue().ownerId().equals(ownerId))
                .map(
                        entry ->
                                new VectorMatch(
                                        entry.getKey(),
                                        cosineSimilarity(query, entry.getValue().embedding())))
                .sorted(Comparator.comparingDouble(VectorMatch::score).reversed())
                .limit(topK)
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
