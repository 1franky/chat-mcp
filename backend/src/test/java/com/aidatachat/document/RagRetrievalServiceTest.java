package com.aidatachat.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aidatachat.adapters.out.fake.FakeDocumentRepository;
import com.aidatachat.adapters.out.fake.FakeEmbeddingProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeVectorSearchAdapter;
import com.aidatachat.application.port.in.RagRetrievalUseCase.RetrievalResult;
import com.aidatachat.application.port.in.RagRetrievalUseCase.RetrievedChunk;
import com.aidatachat.application.port.out.EmbeddingProviderPort;
import com.aidatachat.application.port.out.VectorSearchPort.ChunkRecord;
import com.aidatachat.application.service.RagRetrievalService;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagRetrievalServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final FakeDocumentRepository documents = new FakeDocumentRepository();
    private final FakeVectorSearchAdapter vectorSearch = new FakeVectorSearchAdapter();
    private final FakeEmbeddingProviderAdapter embeddingProvider =
            new FakeEmbeddingProviderAdapter();

    @Test
    void returnsNothingAndNeverEmbedsWhenNoDocumentsAreSelected() {
        EmbeddingProviderPort embeddingSpy = mock(EmbeddingProviderPort.class);
        RagRetrievalService service = service(embeddingSpy, 5, 0.0);

        RetrievalResult result = service.retrieve(OWNER, List.of(), "pregunta");

        assertThat(result.chunks()).isEmpty();
        verifyNoInteractions(embeddingSpy);
    }

    @Test
    void ignoresDocumentsThatAreNotReadyAndNeverEmbeds() {
        UUID documentId = storeDocument(DocumentStatus.PROCESSING);
        EmbeddingProviderPort embeddingSpy = mock(EmbeddingProviderPort.class);
        RagRetrievalService service = service(embeddingSpy, 5, 0.0);

        RetrievalResult result = service.retrieve(OWNER, List.of(documentId), "pregunta");

        assertThat(result.chunks()).isEmpty();
        verifyNoInteractions(embeddingSpy);
    }

    @Test
    void ignoresDocumentsThatDoNotBelongToTheOwner() {
        UUID documentId = storeDocument(DocumentStatus.READY);
        RagRetrievalService service = service(embeddingProvider, 5, 0.0);

        RetrievalResult result =
                service.retrieve(UUID.randomUUID(), List.of(documentId), "pregunta");

        assertThat(result.chunks()).isEmpty();
    }

    @Test
    void retrievesAndRanksChunksFromReadyDocuments() {
        UUID documentId = storeDocument(DocumentStatus.READY);
        UUID chunkId = UUID.randomUUID();
        String queryText = "pregunta sobre el informe";
        float[] matchingVector =
                embeddingProvider
                        .embed(FakeEmbeddingProviderAdapter.MODEL_ID, List.of(queryText))
                        .vectors()
                        .getFirst();
        vectorSearch.replaceChunks(
                OWNER,
                documentId,
                List.of(
                        new ChunkRecord(
                                chunkId,
                                0,
                                "contenido relevante",
                                3,
                                "Introduccion",
                                FakeEmbeddingProviderAdapter.MODEL_ID,
                                matchingVector,
                                Instant.now())));
        RagRetrievalService service = service(embeddingProvider, 5, 0.5);

        RetrievalResult result = service.retrieve(OWNER, List.of(documentId), queryText);

        assertThat(result.chunks()).hasSize(1);
        RetrievedChunk chunk = result.chunks().getFirst();
        assertThat(chunk.documentId()).isEqualTo(documentId);
        assertThat(chunk.documentName()).isEqualTo("informe.pdf");
        assertThat(chunk.chunkId()).isEqualTo(chunkId);
        assertThat(chunk.pageNumber()).isEqualTo(3);
        assertThat(chunk.sectionLabel()).isEqualTo("Introduccion");
        assertThat(chunk.content()).isEqualTo("contenido relevante");
        assertThat(chunk.score()).isGreaterThan(0.99);
    }

    @Test
    void excludesChunksBelowTheScoreThreshold() {
        UUID documentId = storeDocument(DocumentStatus.READY);
        vectorSearch.replaceChunks(
                OWNER,
                documentId,
                List.of(
                        new ChunkRecord(
                                UUID.randomUUID(),
                                0,
                                "contenido no relacionado",
                                null,
                                null,
                                FakeEmbeddingProviderAdapter.MODEL_ID,
                                orthogonalVector(),
                                Instant.now())));
        RagRetrievalService service = service(embeddingProvider, 5, 0.9);

        RetrievalResult result = service.retrieve(OWNER, List.of(documentId), "pregunta");

        assertThat(result.chunks()).isEmpty();
    }

    private RagRetrievalService service(
            EmbeddingProviderPort embeddingProvider, int topK, double scoreThreshold) {
        return new RagRetrievalService(
                documents,
                embeddingProvider,
                vectorSearch,
                FakeEmbeddingProviderAdapter.MODEL_ID,
                topK,
                scoreThreshold);
    }

    private UUID storeDocument(DocumentStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        byte[] content = ("contenido-" + id).getBytes();
        documents.save(
                new Document(
                        id,
                        OWNER,
                        "informe.pdf",
                        "storage-key",
                        "application/pdf",
                        content.length,
                        sha256Hex(content),
                        status,
                        null,
                        status == DocumentStatus.READY
                                ? FakeEmbeddingProviderAdapter.MODEL_ID
                                : null,
                        status == DocumentStatus.READY
                                ? FakeEmbeddingProviderAdapter.DIMENSION
                                : null,
                        0,
                        0,
                        now,
                        now));
        return id;
    }

    private float[] orthogonalVector() {
        float[] vector = new float[FakeEmbeddingProviderAdapter.DIMENSION];
        vector[0] = 1f;
        return vector;
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
