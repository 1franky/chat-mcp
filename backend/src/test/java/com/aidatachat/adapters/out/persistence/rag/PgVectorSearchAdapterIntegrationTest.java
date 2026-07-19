package com.aidatachat.adapters.out.persistence.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.application.port.out.VectorSearchPort.ChunkRecord;
import com.aidatachat.application.port.out.VectorSearchPort.VectorMatch;
import com.aidatachat.application.port.out.VectorSearchPort.VectorRecord;
import com.aidatachat.domain.model.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        properties = {
            "app.providers.credential-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "app.integrations.mode=real"
        })
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class PgVectorSearchAdapterIntegrationTest {

    private static final int DIMENSION = 1536;

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_vector_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private VectorSearchPort vectorSearch;
    @Autowired private IdentityUseCase identity;
    @Autowired private JdbcTemplate jdbc;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID documentId;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM rag.message_document");
        jdbc.update("DELETE FROM rag.document_chunk");
        jdbc.update("DELETE FROM rag.document");
        jdbc.update("DELETE FROM identity.spring_session_attributes");
        jdbc.update("DELETE FROM identity.spring_session");
        jdbc.update("DELETE FROM audit.security_audit_event");
        jdbc.update("DELETE FROM identity.app_user");

        ownerId = register("owner@example.test").id();
        otherOwnerId = register("other@example.test").id();
        documentId = UUID.randomUUID();
        insertDocument(documentId, ownerId);
    }

    @Test
    void indexUpdatesTheEmbeddingOfAnExistingChunk() {
        UUID chunkId = UUID.randomUUID();
        insertChunk(chunkId, documentId, ownerId, 0);
        float[] embedding = unitVector(0);

        vectorSearch.index(ownerId, documentId, List.of(new VectorRecord(chunkId, embedding)));

        List<VectorMatch> matches = vectorSearch.search(ownerId, List.of(documentId), embedding, 5);
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().chunkId()).isEqualTo(chunkId);
        assertThat(matches.getFirst().score()).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void indexFailsWhenTheChunkDoesNotAlreadyExist() {
        assertThatThrownBy(
                        () ->
                                vectorSearch.index(
                                        ownerId,
                                        documentId,
                                        List.of(
                                                new VectorRecord(
                                                        UUID.randomUUID(), unitVector(0)))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void searchRanksTheClosestChunkFirstAndIsolatesByOwner() {
        UUID closeChunk = UUID.randomUUID();
        UUID farChunk = UUID.randomUUID();
        insertChunk(closeChunk, documentId, ownerId, 0);
        insertChunk(farChunk, documentId, ownerId, 1);
        vectorSearch.index(
                ownerId,
                documentId,
                List.of(
                        new VectorRecord(closeChunk, unitVector(0)),
                        new VectorRecord(farChunk, unitVector(1))));

        List<VectorMatch> matches =
                vectorSearch.search(ownerId, List.of(documentId), unitVector(0), 5);
        assertThat(matches.getFirst().chunkId()).isEqualTo(closeChunk);

        assertThat(vectorSearch.search(otherOwnerId, List.of(documentId), unitVector(0), 5))
                .isEmpty();
    }

    @Test
    void searchRestrictsResultsToTheRequestedDocuments() {
        UUID otherDocumentId = UUID.randomUUID();
        insertDocument(otherDocumentId, ownerId);
        UUID inScopeChunk = UUID.randomUUID();
        UUID outOfScopeChunk = UUID.randomUUID();
        insertChunk(inScopeChunk, documentId, ownerId, 0);
        insertChunk(outOfScopeChunk, otherDocumentId, ownerId, 0);
        vectorSearch.index(
                ownerId, documentId, List.of(new VectorRecord(inScopeChunk, unitVector(0))));
        vectorSearch.index(
                ownerId,
                otherDocumentId,
                List.of(new VectorRecord(outOfScopeChunk, unitVector(0))));

        List<VectorMatch> matches =
                vectorSearch.search(ownerId, List.of(documentId), unitVector(0), 5);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().chunkId()).isEqualTo(inScopeChunk);
    }

    @Test
    void findByIdsResolvesContentScopedToOwner() {
        UUID chunkId = UUID.randomUUID();
        insertChunk(chunkId, documentId, ownerId, 0);

        List<com.aidatachat.domain.model.DocumentChunk> found =
                vectorSearch.findByIds(ownerId, List.of(chunkId));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("contenido de prueba");
        assertThat(vectorSearch.findByIds(otherOwnerId, List.of(chunkId))).isEmpty();
    }

    @Test
    void deleteByDocumentRemovesAllOfItsChunks() {
        UUID chunkId = UUID.randomUUID();
        insertChunk(chunkId, documentId, ownerId, 0);
        vectorSearch.index(ownerId, documentId, List.of(new VectorRecord(chunkId, unitVector(0))));

        vectorSearch.deleteByDocument(ownerId, documentId);

        Integer remaining =
                jdbc.queryForObject(
                        "SELECT count(*) FROM rag.document_chunk WHERE document_id = ?",
                        Integer.class,
                        documentId);
        assertThat(remaining).isZero();
    }

    @Test
    void replaceChunksIsIdempotentAcrossReprocessingAttempts() {
        UUID firstChunk = UUID.randomUUID();
        vectorSearch.replaceChunks(
                ownerId, documentId, List.of(chunkRecord(firstChunk, 0, unitVector(0))));

        UUID secondChunk = UUID.randomUUID();
        vectorSearch.replaceChunks(
                ownerId, documentId, List.of(chunkRecord(secondChunk, 0, unitVector(1))));

        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM rag.document_chunk WHERE document_id = ?",
                        Integer.class,
                        documentId);
        assertThat(count).isEqualTo(1);
        List<VectorMatch> matches =
                vectorSearch.search(ownerId, List.of(documentId), unitVector(1), 5);
        assertThat(matches.getFirst().chunkId()).isEqualTo(secondChunk);
    }

    @Test
    void replaceChunksRollsBackTheDeleteWhenTheInsertBatchFails() {
        UUID originalChunk = UUID.randomUUID();
        vectorSearch.replaceChunks(
                ownerId, documentId, List.of(chunkRecord(originalChunk, 0, unitVector(0))));

        assertThatThrownBy(
                        () ->
                                vectorSearch.replaceChunks(
                                        ownerId,
                                        documentId,
                                        List.of(
                                                // Duplicate chunk_index within the same document
                                                // violates the UNIQUE(document_id, chunk_index)
                                                // constraint mid-batch.
                                                chunkRecord(UUID.randomUUID(), 0, unitVector(1)),
                                                chunkRecord(UUID.randomUUID(), 0, unitVector(2)))))
                .isInstanceOf(DataAccessException.class);

        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM rag.document_chunk WHERE document_id = ?",
                        Integer.class,
                        documentId);
        assertThat(count).isEqualTo(1);
        List<VectorMatch> matches =
                vectorSearch.search(ownerId, List.of(documentId), unitVector(0), 5);
        assertThat(matches.getFirst().chunkId()).isEqualTo(originalChunk);
    }

    private ChunkRecord chunkRecord(UUID chunkId, int chunkIndex, float[] embedding) {
        return new ChunkRecord(
                chunkId,
                chunkIndex,
                "contenido de prueba",
                null,
                null,
                "test-embedding-model",
                embedding,
                Instant.now());
    }

    private UserAccount register(String email) {
        return identity.register(
                new RegisterCommand(
                        email, "Test User", "correct-horse-battery-staple", "127.0.0.1"));
    }

    private void insertDocument(UUID id, UUID ownerId) {
        jdbc.update(
                """
                INSERT INTO rag.document
                    (id, owner_id, original_filename, storage_key, mime_type, byte_size,
                     content_hash, status, embedding_model_id, embedding_dimension, chunk_count,
                     version, created_at, updated_at)
                VALUES (?, ?, 'informe.pdf', 'storage-key', 'application/pdf', 1024, ?, 'READY',
                        'test-embedding-model', ?, 0, 0, now(), now())
                """,
                id,
                ownerId,
                contentHashFor(id),
                DIMENSION);
    }

    private String contentHashFor(UUID id) {
        String hex = id.toString().replace("-", "");
        return (hex + hex + hex + hex).substring(0, 64);
    }

    private void insertChunk(UUID id, UUID documentId, UUID ownerId, int chunkIndex) {
        jdbc.update(
                """
                INSERT INTO rag.document_chunk
                    (id, document_id, owner_id, chunk_index, content, embedding_model_id,
                     embedding, created_at)
                VALUES (?, ?, ?, ?, ?, 'test-embedding-model', ?::vector, now())
                """,
                id,
                documentId,
                ownerId,
                chunkIndex,
                "contenido de prueba",
                zeroVectorLiteral());
    }

    private String zeroVectorLiteral() {
        return IntStream.range(0, DIMENSION)
                .mapToObj(i -> "0")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private float[] unitVector(int index) {
        float[] vector = new float[DIMENSION];
        vector[index] = 1f;
        return vector;
    }
}
