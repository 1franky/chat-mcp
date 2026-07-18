package com.aidatachat.application.service;

import com.aidatachat.application.exception.DocumentProcessingException;
import com.aidatachat.application.port.in.DocumentProcessingUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.DocumentStoragePort;
import com.aidatachat.application.port.out.DocumentTextExtractionPort;
import com.aidatachat.application.port.out.DocumentTextExtractionPort.ExtractedDocument;
import com.aidatachat.application.port.out.EmbeddingProviderPort;
import com.aidatachat.application.port.out.EmbeddingProviderPort.EmbeddingBatch;
import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.application.port.out.VectorSearchPort.ChunkRecord;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the pipeline that takes a document from {@code UPLOADED} to {@code READY}:
 * extraction (with a timeout) → chunking ({@link TextChunker}) → embeddings (batched) →
 * {@link VectorSearchPort#replaceChunks} → {@code READY}. Any failure at any stage marks the
 * document {@code FAILED} with a bounded, non-sensitive {@code failureReason} — never a stack
 * trace or document content.
 *
 * <p>Guards against resurrecting a document deleted mid-processing (see
 * {@code DocumentJpaAdapter.save()}, which inserts a fresh row when it doesn't find one to
 * update) by re-checking existence immediately before each terminal {@code save()}. This narrows,
 * but does not eliminate, the race window.
 */
public final class DocumentProcessingService implements DocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documents;
    private final DocumentStoragePort storage;
    private final DocumentTextExtractionPort extraction;
    private final EmbeddingProviderPort embeddingProvider;
    private final VectorSearchPort vectorSearch;
    private final AuditRepository audit;
    private final Clock clock;
    private final ExecutorService extractionExecutor;
    private final long extractionTimeoutSeconds;
    private final int chunkSizeChars;
    private final int overlapChars;
    private final int maxChunksPerDocument;
    private final String embeddingModelId;
    private final int embeddingBatchSize;

    public DocumentProcessingService(
            DocumentRepository documents,
            DocumentStoragePort storage,
            DocumentTextExtractionPort extraction,
            EmbeddingProviderPort embeddingProvider,
            VectorSearchPort vectorSearch,
            AuditRepository audit,
            Clock clock,
            ExecutorService extractionExecutor,
            long extractionTimeoutSeconds,
            int chunkSizeChars,
            int overlapChars,
            int maxChunksPerDocument,
            String embeddingModelId,
            int embeddingBatchSize) {
        this.documents = Objects.requireNonNull(documents);
        this.storage = Objects.requireNonNull(storage);
        this.extraction = Objects.requireNonNull(extraction);
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider);
        this.vectorSearch = Objects.requireNonNull(vectorSearch);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.extractionExecutor = Objects.requireNonNull(extractionExecutor);
        this.extractionTimeoutSeconds = extractionTimeoutSeconds;
        this.chunkSizeChars = chunkSizeChars;
        this.overlapChars = overlapChars;
        this.maxChunksPerDocument = maxChunksPerDocument;
        this.embeddingModelId = Objects.requireNonNull(embeddingModelId);
        this.embeddingBatchSize = embeddingBatchSize;
    }

    @Override
    public void processDocument(UUID ownerId, UUID documentId) {
        Optional<Document> initial = documents.findByIdAndOwnerId(documentId, ownerId);
        if (initial.isEmpty()) {
            return;
        }
        Document document = initial.get();
        markProcessing(document);

        String reasonCode;
        int chunkCount = 0;
        Integer embeddingDimension = null;
        try {
            byte[] content = readAll(ownerId, document.storageKey());
            String extension = extractExtension(document.originalFilename());
            ExtractedDocument extracted =
                    extractWithTimeout(extension, document.mimeType(), content);

            List<TextChunker.Chunk> textChunks =
                    TextChunker.chunk(extracted.pages(), chunkSizeChars, overlapChars);
            if (textChunks.isEmpty()) {
                throw new DocumentProcessingException("no_extractable_text");
            }
            if (textChunks.size() > maxChunksPerDocument) {
                throw new DocumentProcessingException("too_many_chunks");
            }

            EmbeddedChunks embedded = embedChunks(textChunks);
            chunkCount = embedded.chunks().size();
            embeddingDimension = embedded.dimension();

            if (documents.findByIdAndOwnerId(documentId, ownerId).isPresent()) {
                vectorSearch.replaceChunks(ownerId, documentId, embedded.chunks());
            }
            reasonCode = null;
        } catch (DocumentProcessingException e) {
            reasonCode = e.reasonCode();
        } catch (RuntimeException e) {
            log.warn(
                    "Document processing failed for document {}: {}",
                    documentId,
                    e.getClass().getSimpleName());
            reasonCode = "processing_failed";
        }

        finalizeDocument(ownerId, documentId, reasonCode, chunkCount, embeddingDimension);
    }

    private void markProcessing(Document document) {
        documents.save(withStatus(document, DocumentStatus.PROCESSING, null, null, null, 0));
    }

    private void finalizeDocument(
            UUID ownerId,
            UUID documentId,
            String reasonCode,
            int chunkCount,
            Integer embeddingDimension) {
        Optional<Document> current = documents.findByIdAndOwnerId(documentId, ownerId);
        if (current.isEmpty()) {
            return;
        }
        Document document = current.get();
        if (reasonCode == null) {
            documents.save(
                    withStatus(
                            document,
                            DocumentStatus.READY,
                            null,
                            embeddingModelId,
                            embeddingDimension,
                            chunkCount));
            appendAudit(
                    ownerId,
                    documentId,
                    "DOCUMENT_PROCESSED",
                    true,
                    Map.of("chunkCount", String.valueOf(chunkCount)));
        } else {
            documents.save(withStatus(document, DocumentStatus.FAILED, reasonCode, null, null, 0));
            appendAudit(
                    ownerId,
                    documentId,
                    "DOCUMENT_PROCESSING_FAILED",
                    false,
                    Map.of("reasonCode", reasonCode));
        }
    }

    private Document withStatus(
            Document document,
            DocumentStatus status,
            String failureReason,
            String embeddingModelIdValue,
            Integer embeddingDimension,
            int chunkCount) {
        return new Document(
                document.id(),
                document.ownerId(),
                document.originalFilename(),
                document.storageKey(),
                document.mimeType(),
                document.byteSize(),
                document.contentHash(),
                status,
                failureReason,
                embeddingModelIdValue,
                embeddingDimension,
                chunkCount,
                document.version(),
                document.createdAt(),
                clock.instant());
    }

    private byte[] readAll(UUID ownerId, String storageKey) {
        try (InputStream in = storage.open(ownerId, storageKey)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new DocumentProcessingException("extraction_failed", e);
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private ExtractedDocument extractWithTimeout(
            String extension, String mimeType, byte[] content) {
        Future<ExtractedDocument> future =
                extractionExecutor.submit(() -> extraction.extract(extension, mimeType, content));
        try {
            return future.get(extractionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DocumentProcessingException("extraction_timeout", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DocumentProcessingException cause) {
                throw cause;
            }
            throw new DocumentProcessingException("extraction_failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentProcessingException("extraction_failed", e);
        }
    }

    private record EmbeddedChunks(List<ChunkRecord> chunks, int dimension) {}

    private EmbeddedChunks embedChunks(List<TextChunker.Chunk> textChunks) {
        List<ChunkRecord> chunkRecords = new ArrayList<>(textChunks.size());
        Instant createdAt = clock.instant();
        int dimension = 0;
        for (int start = 0; start < textChunks.size(); start += embeddingBatchSize) {
            List<TextChunker.Chunk> batch =
                    textChunks.subList(
                            start, Math.min(start + embeddingBatchSize, textChunks.size()));
            List<String> texts = batch.stream().map(TextChunker.Chunk::content).toList();
            EmbeddingBatch embedded;
            try {
                embedded = embeddingProvider.embed(embeddingModelId, texts);
            } catch (RuntimeException e) {
                throw new DocumentProcessingException("embedding_failed", e);
            }
            dimension = embedded.dimension();
            for (int i = 0; i < batch.size(); i++) {
                TextChunker.Chunk textChunk = batch.get(i);
                chunkRecords.add(
                        new ChunkRecord(
                                UUID.randomUUID(),
                                textChunk.chunkIndex(),
                                textChunk.content(),
                                textChunk.pageNumber(),
                                textChunk.sectionLabel(),
                                embeddingModelId,
                                embedded.vectors().get(i),
                                createdAt));
            }
        }
        return new EmbeddedChunks(chunkRecords, dimension);
    }

    private void appendAudit(
            UUID actorId,
            UUID targetId,
            String eventType,
            boolean success,
            Map<String, String> metadata) {
        audit.append(
                new AuditRepository.AuditEvent(
                        actorId, targetId, eventType, success, clock.instant(), null, metadata));
    }
}
