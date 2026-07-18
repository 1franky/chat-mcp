package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidatachat.application.port.out.DocumentRepository.DocumentPage;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeDocumentRepositoryTest {

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();

    @Test
    void savesAndFindsADocumentOwnedByTheCaller() {
        Document document = document(UUID.randomUUID(), owner, DocumentStatus.UPLOADED, 1);

        repository.save(document);

        assertThat(repository.existsByIdAndOwnerId(document.id(), owner)).isTrue();
        assertThat(repository.findByIdAndOwnerId(document.id(), owner)).contains(document);
    }

    @Test
    void isolatesDocumentsByOwner() {
        Document document = document(UUID.randomUUID(), owner, DocumentStatus.UPLOADED, 1);
        repository.save(document);

        assertThat(repository.existsByIdAndOwnerId(document.id(), otherOwner)).isFalse();
        assertThat(repository.findByIdAndOwnerId(document.id(), otherOwner)).isEmpty();

        repository.deleteByIdAndOwnerId(document.id(), otherOwner);
        assertThat(repository.existsByIdAndOwnerId(document.id(), owner)).isTrue();
    }

    @Test
    void deletesOnlyWhenOwnerMatches() {
        Document document = document(UUID.randomUUID(), owner, DocumentStatus.UPLOADED, 1);
        repository.save(document);

        repository.deleteByIdAndOwnerId(document.id(), owner);

        assertThat(repository.existsByIdAndOwnerId(document.id(), owner)).isFalse();
    }

    @Test
    void findsByOwnerAndContentHashForIdempotencyAndIsolatesByOwner() {
        Document document = document(UUID.randomUUID(), owner, DocumentStatus.UPLOADED, 1);
        repository.save(document);

        assertThat(repository.findByOwnerIdAndContentHash(owner, document.contentHash()))
                .contains(document);
        assertThat(repository.findByOwnerIdAndContentHash(otherOwner, document.contentHash()))
                .isEmpty();
        assertThat(repository.findByOwnerIdAndContentHash(owner, "b".repeat(64))).isEmpty();
    }

    @Test
    void filtersAndPaginatesByOwnerAndStatus() {
        repository.save(document(UUID.randomUUID(), owner, DocumentStatus.READY, 1));
        repository.save(document(UUID.randomUUID(), owner, DocumentStatus.READY, 2));
        repository.save(document(UUID.randomUUID(), owner, DocumentStatus.FAILED, 3));
        repository.save(document(UUID.randomUUID(), otherOwner, DocumentStatus.READY, 4));

        DocumentPage readyOnly = repository.findAllByOwnerId(owner, DocumentStatus.READY, 0, 10);
        assertThat(readyOnly.totalElements()).isEqualTo(2);
        assertThat(readyOnly.items()).allMatch(d -> d.status() == DocumentStatus.READY);

        DocumentPage allStatuses = repository.findAllByOwnerId(owner, null, 0, 10);
        assertThat(allStatuses.totalElements()).isEqualTo(3);

        DocumentPage firstPage = repository.findAllByOwnerId(owner, null, 0, 2);
        DocumentPage secondPage = repository.findAllByOwnerId(owner, null, 1, 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    private Document document(UUID id, UUID ownerId, DocumentStatus status, long secondOffset) {
        Instant now = Instant.parse("2026-07-17T10:00:00Z").plusSeconds(secondOffset);
        boolean ready = status == DocumentStatus.READY;
        return new Document(
                id,
                ownerId,
                "informe.pdf",
                "storage-key-" + id,
                "application/pdf",
                1024,
                "a".repeat(64),
                status,
                status == DocumentStatus.FAILED ? "extraction failed" : null,
                ready ? "fake-embedding-v1" : null,
                ready ? 1536 : null,
                0,
                0,
                now,
                now);
    }
}
