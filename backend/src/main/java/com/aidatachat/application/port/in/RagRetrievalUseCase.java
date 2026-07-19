package com.aidatachat.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Retrieves chunks relevant to a query, scoped strictly to a caller-supplied set of documents. Not
 * exposed over HTTP — driven internally by {@code ChatService} before generating a reply, only when
 * the active conversation has ≥1 document selected (opt-in: no selection means this is never
 * called, so a plain conversation has zero retrieval latency/cost).
 */
public interface RagRetrievalUseCase {

    RetrievalResult retrieve(UUID ownerId, List<UUID> documentIds, String queryText);

    record RetrievalResult(List<RetrievedChunk> chunks) {

        public RetrievalResult {
            chunks = List.copyOf(chunks);
        }
    }

    record RetrievedChunk(
            UUID documentId,
            String documentName,
            UUID chunkId,
            Integer pageNumber,
            String sectionLabel,
            String content,
            double score) {}
}
