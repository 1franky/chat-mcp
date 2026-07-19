package com.aidatachat.application.service;

import com.aidatachat.application.port.in.RagRetrievalUseCase;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.EmbeddingProviderPort;
import com.aidatachat.application.port.out.EmbeddingProviderPort.EmbeddingBatch;
import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.application.port.out.VectorSearchPort.VectorMatch;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentChunk;
import com.aidatachat.domain.model.DocumentStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vector-only retrieval scoped strictly to caller-supplied documents: validates ownership + {@code
 * READY} status (silently ignoring documents that are deleted/not-yet-processed — a stale selection
 * never breaks the chat, it just yields no context for that turn), embeds the query, searches, and
 * filters by a configurable score threshold. Hybrid full-text search is explicitly deferred (see
 * docs/rag.md) — the project spec conditions it on "cuando aporte valor".
 */
public final class RagRetrievalService implements RagRetrievalUseCase {

    private final DocumentRepository documents;
    private final EmbeddingProviderPort embeddingProvider;
    private final VectorSearchPort vectorSearch;
    private final String embeddingModelId;
    private final int topK;
    private final double scoreThreshold;

    public RagRetrievalService(
            DocumentRepository documents,
            EmbeddingProviderPort embeddingProvider,
            VectorSearchPort vectorSearch,
            String embeddingModelId,
            int topK,
            double scoreThreshold) {
        this.documents = Objects.requireNonNull(documents);
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider);
        this.vectorSearch = Objects.requireNonNull(vectorSearch);
        this.embeddingModelId = Objects.requireNonNull(embeddingModelId);
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public RetrievalResult retrieve(UUID ownerId, List<UUID> documentIds, String queryText) {
        if (documentIds.isEmpty()) {
            return new RetrievalResult(List.of());
        }
        Map<UUID, Document> readyDocuments =
                documents.findAllByIdsAndOwnerId(documentIds, ownerId).stream()
                        .filter(document -> document.status() == DocumentStatus.READY)
                        .collect(Collectors.toMap(Document::id, Function.identity()));
        if (readyDocuments.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        EmbeddingBatch embedded = embeddingProvider.embed(embeddingModelId, List.of(queryText));
        float[] queryVector = embedded.vectors().getFirst();

        List<VectorMatch> matches =
                vectorSearch.search(ownerId, readyDocuments.keySet(), queryVector, topK);
        Map<UUID, Double> scoreByChunkId =
                matches.stream()
                        .filter(match -> match.score() >= scoreThreshold)
                        .collect(Collectors.toMap(VectorMatch::chunkId, VectorMatch::score));
        if (scoreByChunkId.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        List<DocumentChunk> chunks = vectorSearch.findByIds(ownerId, scoreByChunkId.keySet());
        List<RetrievedChunk> retrieved =
                chunks.stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (DocumentChunk chunk) ->
                                                        scoreByChunkId.get(chunk.id()))
                                        .reversed())
                        .map(
                                chunk ->
                                        new RetrievedChunk(
                                                chunk.documentId(),
                                                readyDocuments
                                                        .get(chunk.documentId())
                                                        .originalFilename(),
                                                chunk.id(),
                                                chunk.pageNumber(),
                                                chunk.sectionLabel(),
                                                chunk.content(),
                                                scoreByChunkId.get(chunk.id())))
                        .toList();
        return new RetrievalResult(retrieved);
    }
}
