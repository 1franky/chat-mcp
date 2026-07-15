package com.aidatachat.application.port.out;

import java.util.List;
import java.util.UUID;

public interface VectorSearchPort {

    void index(UUID ownerId, UUID documentId, List<VectorRecord> vectors);

    List<VectorMatch> search(UUID ownerId, float[] query, int topK);

    void deleteByDocument(UUID ownerId, UUID documentId);

    record VectorRecord(UUID chunkId, float[] embedding) {}

    record VectorMatch(UUID chunkId, double score) {}
}
