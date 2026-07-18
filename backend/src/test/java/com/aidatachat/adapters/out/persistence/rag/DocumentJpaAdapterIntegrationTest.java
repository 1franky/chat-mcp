package com.aidatachat.adapters.out.persistence.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.DocumentRepository.DocumentPage;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import com.aidatachat.domain.model.UserAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
class DocumentJpaAdapterIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_document_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private DocumentRepository documents;
    @Autowired private IdentityUseCase identity;
    @Autowired private JdbcTemplate jdbc;

    private UUID ownerId;
    private UUID otherOwnerId;

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
    }

    @Test
    void savesFindsAndDeletesADocumentIsolatedByOwner() {
        Document document = document(UUID.randomUUID(), ownerId, "a".repeat(64));

        documents.save(document);

        assertThat(documents.findByIdAndOwnerId(document.id(), ownerId)).contains(document);
        assertThat(documents.existsByIdAndOwnerId(document.id(), otherOwnerId)).isFalse();
        assertThat(documents.findByIdAndOwnerId(document.id(), otherOwnerId)).isEmpty();

        documents.deleteByIdAndOwnerId(document.id(), otherOwnerId);
        assertThat(documents.existsByIdAndOwnerId(document.id(), ownerId)).isTrue();

        documents.deleteByIdAndOwnerId(document.id(), ownerId);
        assertThat(documents.existsByIdAndOwnerId(document.id(), ownerId)).isFalse();
    }

    @Test
    void incrementsVersionOnUpdate() {
        Document document = document(UUID.randomUUID(), ownerId, "b".repeat(64));
        documents.save(document);

        Document reloaded = documents.findByIdAndOwnerId(document.id(), ownerId).orElseThrow();
        Document withFailure =
                new Document(
                        reloaded.id(),
                        reloaded.ownerId(),
                        reloaded.originalFilename(),
                        reloaded.storageKey(),
                        reloaded.mimeType(),
                        reloaded.byteSize(),
                        reloaded.contentHash(),
                        DocumentStatus.FAILED,
                        "extraction timed out",
                        null,
                        null,
                        0,
                        reloaded.version(),
                        reloaded.createdAt(),
                        Instant.now());

        Document saved = documents.save(withFailure);

        assertThat(saved.version()).isGreaterThan(reloaded.version());
        assertThat(saved.status()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void enforcesUniqueContentHashPerOwnerButAllowsItAcrossOwners() {
        String hash = "c".repeat(64);
        documents.save(document(UUID.randomUUID(), ownerId, hash));

        assertThatThrownBy(() -> documents.save(document(UUID.randomUUID(), ownerId, hash)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Document forOtherOwner = document(UUID.randomUUID(), otherOwnerId, hash);
        assertThat(documents.save(forOtherOwner)).isNotNull();
    }

    @Test
    void findsByOwnerAndContentHashForIdempotencyAndIsolatesByOwner() {
        String hash = "d".repeat(64);
        Document document = document(UUID.randomUUID(), ownerId, hash);
        documents.save(document);

        assertThat(documents.findByOwnerIdAndContentHash(ownerId, hash))
                .map(Document::id)
                .contains(document.id());
        assertThat(documents.findByOwnerIdAndContentHash(otherOwnerId, hash)).isEmpty();
        assertThat(documents.findByOwnerIdAndContentHash(ownerId, "e".repeat(64))).isEmpty();
    }

    @Test
    void filtersAndPaginatesDocumentsByOwnerAndStatus() {
        documents.save(readyDocument(UUID.randomUUID(), ownerId, "d".repeat(64)));
        documents.save(readyDocument(UUID.randomUUID(), ownerId, "e".repeat(64)));
        documents.save(document(UUID.randomUUID(), ownerId, "f".repeat(64)));
        documents.save(readyDocument(UUID.randomUUID(), otherOwnerId, "0".repeat(64)));

        DocumentPage readyOnly = documents.findAllByOwnerId(ownerId, DocumentStatus.READY, 0, 10);
        assertThat(readyOnly.totalElements()).isEqualTo(2);

        DocumentPage allStatuses = documents.findAllByOwnerId(ownerId, null, 0, 10);
        assertThat(allStatuses.totalElements()).isEqualTo(3);
    }

    @Test
    void cascadesDeleteWhenTheOwningUserIsDeleted() {
        Document document = document(UUID.randomUUID(), ownerId, "1".repeat(64));
        documents.save(document);

        jdbc.update("DELETE FROM identity.app_user WHERE id = ?", ownerId);

        Integer remaining =
                jdbc.queryForObject(
                        "SELECT count(*) FROM rag.document WHERE id = ?",
                        Integer.class,
                        document.id());
        assertThat(remaining).isZero();
    }

    private UserAccount register(String email) {
        return identity.register(
                new RegisterCommand(
                        email, "Test User", "correct-horse-battery-staple", "127.0.0.1"));
    }

    private Document document(UUID id, UUID ownerId, String contentHash) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Document(
                id,
                ownerId,
                "informe.pdf",
                "storage-key-" + id,
                "application/pdf",
                1024,
                contentHash,
                DocumentStatus.UPLOADED,
                null,
                null,
                null,
                0,
                0,
                now,
                now);
    }

    private Document readyDocument(UUID id, UUID ownerId, String contentHash) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Document(
                id,
                ownerId,
                "informe.pdf",
                "storage-key-" + id,
                "application/pdf",
                1024,
                contentHash,
                DocumentStatus.READY,
                null,
                "fake-embedding-v1",
                1536,
                3,
                0,
                now,
                now);
    }
}
