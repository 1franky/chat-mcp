package com.aidatachat.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aidatachat.adapters.out.extraction.DocumentTextExtractorAdapter;
import com.aidatachat.adapters.out.fake.FakeDocumentRepository;
import com.aidatachat.adapters.out.fake.FakeDocumentStorageAdapter;
import com.aidatachat.adapters.out.fake.FakeEmbeddingProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeVectorSearchAdapter;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.AuditRepository.AuditEvent;
import com.aidatachat.application.port.out.DocumentTextExtractionPort;
import com.aidatachat.application.port.out.DocumentTextExtractionPort.ExtractedDocument;
import com.aidatachat.application.port.out.DocumentTextExtractionPort.ExtractedPage;
import com.aidatachat.application.port.out.VectorSearchPort.VectorMatch;
import com.aidatachat.application.service.DocumentProcessingService;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DocumentProcessingServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final FakeDocumentRepository documents = new FakeDocumentRepository();
    private final FakeDocumentStorageAdapter storage = new FakeDocumentStorageAdapter();
    private final FakeVectorSearchAdapter vectorSearch = new FakeVectorSearchAdapter();
    private final AuditRepository audit = mock(AuditRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Test
    void takesADocumentFromUploadedToReadyWithRealExtractionChunkingAndEmbedding() {
        String content =
                "Primer parrafo con contenido suficiente para generar embeddings de prueba.\n\n"
                        + "Segundo parrafo, tambien con contenido real para el chunking.";
        UUID id =
                storeDocument(
                        "informe.txt", content.getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentProcessingService service =
                service(
                        new DocumentTextExtractorAdapter(200, 2_000_000),
                        new FakeEmbeddingProviderAdapter(),
                        30,
                        1800,
                        200,
                        500);

        service.processDocument(OWNER, id);

        Document result = documents.findByIdAndOwnerId(id, OWNER).orElseThrow();
        assertThat(result.status()).isEqualTo(DocumentStatus.READY);
        assertThat(result.embeddingModelId()).isEqualTo(FakeEmbeddingProviderAdapter.MODEL_ID);
        assertThat(result.embeddingDimension()).isEqualTo(FakeEmbeddingProviderAdapter.DIMENSION);
        assertThat(result.chunkCount()).isGreaterThan(0);
        assertThat(result.failureReason()).isNull();

        List<VectorMatch> matches =
                vectorSearch.search(
                        OWNER, List.of(id), new float[FakeEmbeddingProviderAdapter.DIMENSION], 10);
        assertThat(matches).hasSize(result.chunkCount());

        verify(audit)
                .append(
                        argThat(
                                (AuditEvent event) ->
                                        event.eventType().equals("DOCUMENT_PROCESSED")
                                                && event.success()
                                                && event.targetId().equals(id)));
    }

    @Test
    void marksTheDocumentFailedWhenExtractionTimesOut() {
        UUID id =
                storeDocument(
                        "informe.txt", "contenido".getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentTextExtractionPort slowExtraction =
                (extension, mimeType, content) -> {
                    try {
                        Thread.sleep(3_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new ExtractedDocument(List.of(new ExtractedPage(null, null, "texto")));
                };
        DocumentProcessingService service =
                service(slowExtraction, new FakeEmbeddingProviderAdapter(), 1, 1800, 200, 500);

        service.processDocument(OWNER, id);

        assertFailed(id, "extraction_timeout");
    }

    @Test
    void marksTheDocumentFailedForContentThatIsNotValidUtf8() {
        byte[] malformed = new byte[] {(byte) 0xFF, (byte) 0xFE, 'h', 'i'};
        UUID id = storeDocument("informe.txt", malformed, "text/plain");
        DocumentProcessingService service =
                service(
                        new DocumentTextExtractorAdapter(200, 2_000_000),
                        new FakeEmbeddingProviderAdapter(),
                        30,
                        1800,
                        200,
                        500);

        service.processDocument(OWNER, id);

        assertFailed(id, "unsupported_text_encoding");
    }

    @Test
    void marksTheDocumentFailedWhenPdfPageCountExceedsTheConfiguredMaximum() {
        byte[] content = DocumentFixtures.pdfBytesWithPages(List.of("uno", "dos"));
        UUID id = storeDocument("informe.pdf", content, "application/pdf");
        DocumentProcessingService service =
                service(
                        new DocumentTextExtractorAdapter(1, 2_000_000),
                        new FakeEmbeddingProviderAdapter(),
                        30,
                        1800,
                        200,
                        500);

        service.processDocument(OWNER, id);

        assertFailed(id, "too_many_pages");
    }

    @Test
    void marksTheDocumentFailedWhenChunkCountExceedsTheConfiguredMaximum() {
        UUID id =
                storeDocument(
                        "informe.txt", "contenido".getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentTextExtractionPort twoPageExtraction =
                (extension, mimeType, content) ->
                        new ExtractedDocument(
                                List.of(
                                        new ExtractedPage(1, null, "primera pagina con texto"),
                                        new ExtractedPage(2, null, "segunda pagina con texto")));
        DocumentProcessingService service =
                service(twoPageExtraction, new FakeEmbeddingProviderAdapter(), 30, 1800, 200, 1);

        service.processDocument(OWNER, id);

        assertFailed(id, "too_many_chunks");
    }

    @Test
    void marksTheDocumentFailedWhenNoTextCouldBeExtracted() {
        UUID id =
                storeDocument(
                        "informe.txt", "contenido".getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentTextExtractionPort blankExtraction =
                (extension, mimeType, content) ->
                        new ExtractedDocument(List.of(new ExtractedPage(null, null, "   \n\n  ")));
        DocumentProcessingService service =
                service(blankExtraction, new FakeEmbeddingProviderAdapter(), 30, 1800, 200, 500);

        service.processDocument(OWNER, id);

        assertFailed(id, "no_extractable_text");
    }

    @Test
    void marksTheDocumentFailedWhenTheEmbeddingProviderFails() {
        UUID id =
                storeDocument(
                        "informe.txt",
                        "contenido de prueba".getBytes(StandardCharsets.UTF_8),
                        "text/plain");
        DocumentProcessingService service =
                service(
                        new DocumentTextExtractorAdapter(200, 2_000_000),
                        (modelId, inputs) -> {
                            throw new RuntimeException("embedding provider is down");
                        },
                        30,
                        1800,
                        200,
                        500);

        service.processDocument(OWNER, id);

        assertFailed(id, "embedding_failed");
    }

    @Test
    void fallsBackToAGenericReasonForAnUnanticipatedFailure() {
        // The document row references a storage key that was never actually stored (skips
        // storage.store), so storage.open() throws DocumentStorageException — a RuntimeException
        // not wrapped by readAll()'s IOException-only catch, exercising the outer catch-all.
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        documents.save(
                new Document(
                        id,
                        OWNER,
                        "informe.txt",
                        "never-stored-key",
                        "text/plain",
                        9,
                        sha256Hex("contenido".getBytes(StandardCharsets.UTF_8)),
                        DocumentStatus.UPLOADED,
                        null,
                        null,
                        null,
                        0,
                        0,
                        now,
                        now));
        DocumentProcessingService service =
                service(
                        new DocumentTextExtractorAdapter(200, 2_000_000),
                        new FakeEmbeddingProviderAdapter(),
                        30,
                        1800,
                        200,
                        500);

        service.processDocument(OWNER, id);

        assertFailed(id, "processing_failed");
        verify(audit)
                .append(
                        argThat(
                                (AuditEvent event) ->
                                        event.eventType().equals("DOCUMENT_PROCESSING_FAILED")
                                                && !event.success()));
    }

    @Test
    void doesNotResurrectADocumentDeletedWhileItWasBeingProcessed() {
        UUID id =
                storeDocument(
                        "informe.txt", "contenido".getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentTextExtractionPort deletingExtraction =
                (extension, mimeType, content) -> {
                    documents.deleteByIdAndOwnerId(id, OWNER);
                    return new ExtractedDocument(
                            List.of(
                                    new ExtractedPage(
                                            null, null, "contenido suficiente para un chunk")));
                };
        DocumentProcessingService service =
                service(deletingExtraction, new FakeEmbeddingProviderAdapter(), 30, 1800, 200, 500);

        service.processDocument(OWNER, id);

        assertThat(documents.findByIdAndOwnerId(id, OWNER)).isEmpty();
        assertThat(
                        vectorSearch.search(
                                OWNER,
                                List.of(id),
                                new float[FakeEmbeddingProviderAdapter.DIMENSION],
                                10))
                .isEmpty();
    }

    private void assertFailed(UUID id, String reasonCode) {
        Document result = documents.findByIdAndOwnerId(id, OWNER).orElseThrow();
        assertThat(result.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo(reasonCode);
        assertThat(result.chunkCount()).isZero();
        assertThat(result.embeddingModelId()).isNull();
    }

    private DocumentProcessingService service(
            DocumentTextExtractionPort extraction,
            com.aidatachat.application.port.out.EmbeddingProviderPort embeddingProvider,
            long extractionTimeoutSeconds,
            int chunkSizeChars,
            int overlapChars,
            int maxChunksPerDocument) {
        return new DocumentProcessingService(
                documents,
                storage,
                extraction,
                embeddingProvider,
                vectorSearch,
                audit,
                clock,
                executor,
                extractionTimeoutSeconds,
                chunkSizeChars,
                overlapChars,
                maxChunksPerDocument,
                FakeEmbeddingProviderAdapter.MODEL_ID,
                64);
    }

    private UUID storeDocument(String filename, byte[] content, String mimeType) {
        UUID id = UUID.randomUUID();
        String storageKey = storage.store(OWNER, id.toString(), new ByteArrayInputStream(content));
        Instant now = clock.instant();
        documents.save(
                new Document(
                        id,
                        OWNER,
                        filename,
                        storageKey,
                        mimeType,
                        content.length,
                        sha256Hex(content),
                        DocumentStatus.UPLOADED,
                        null,
                        null,
                        null,
                        0,
                        0,
                        now,
                        now));
        return id;
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
