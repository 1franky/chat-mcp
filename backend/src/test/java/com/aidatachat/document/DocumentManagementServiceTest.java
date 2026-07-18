package com.aidatachat.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aidatachat.adapters.out.fake.FakeDocumentRepository;
import com.aidatachat.adapters.out.fake.FakeDocumentStorageAdapter;
import com.aidatachat.adapters.out.mime.TikaDocumentMimeDetectionAdapter;
import com.aidatachat.application.exception.DocumentNotFoundException;
import com.aidatachat.application.exception.DocumentStorageException;
import com.aidatachat.application.exception.DocumentTooLargeException;
import com.aidatachat.application.exception.UnsupportedDocumentTypeException;
import com.aidatachat.application.port.in.DocumentManagementUseCase.DocumentListView;
import com.aidatachat.application.port.in.DocumentManagementUseCase.DocumentView;
import com.aidatachat.application.port.in.DocumentManagementUseCase.UploadDocumentCommand;
import com.aidatachat.application.port.in.DocumentManagementUseCase.UploadDocumentResult;
import com.aidatachat.application.port.in.DocumentProcessingUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.service.DocumentManagementService;
import com.aidatachat.domain.model.DocumentStatus;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentManagementServiceTest {

    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final UUID OWNER = UUID.fromString("40e76e01-d43f-4caf-aa9d-d4d997d451e8");
    private static final UUID OTHER_OWNER = UUID.fromString("7d929830-1a50-4321-93ef-2e85dc0d9032");

    private final FakeDocumentRepository documents = new FakeDocumentRepository();
    private final FakeDocumentStorageAdapter storage = new FakeDocumentStorageAdapter();
    private final AuditRepository audit = mock(AuditRepository.class);
    private final RecordingDocumentProcessing documentProcessing =
            new RecordingDocumentProcessing();
    private final ExecutorService documentProcessingExecutor = Executors.newSingleThreadExecutor();
    private DocumentManagementService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
        service =
                new DocumentManagementService(
                        documents,
                        storage,
                        new TikaDocumentMimeDetectionAdapter(),
                        audit,
                        clock,
                        MAX_UPLOAD_BYTES,
                        documentProcessing,
                        documentProcessingExecutor);
    }

    private static final class RecordingDocumentProcessing implements DocumentProcessingUseCase {
        private final List<UUID[]> calls = Collections.synchronizedList(new ArrayList<>());
        private volatile CountDownLatch latch = new CountDownLatch(0);

        @Override
        public void processDocument(UUID ownerId, UUID documentId) {
            calls.add(new UUID[] {ownerId, documentId});
            latch.countDown();
        }

        void expectCalls(int count) {
            latch = new CountDownLatch(count);
        }

        boolean awaitCalls() {
            try {
                return latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        List<UUID[]> calls() {
            return calls;
        }
    }

    @Test
    void uploadsAPdfDocument() {
        UploadDocumentResult result =
                service.uploadDocument(OWNER, command("informe.pdf", DocumentFixtures.pdfBytes()));

        assertThat(result.created()).isTrue();
        assertThat(result.document().mimeType()).isEqualTo("application/pdf");
        assertThat(result.document().status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(result.document().originalFilename()).isEqualTo("informe.pdf");
    }

    @Test
    void uploadsADocxDocument() {
        UploadDocumentResult result =
                service.uploadDocument(
                        OWNER, command("informe.docx", DocumentFixtures.docxBytes()));

        assertThat(result.created()).isTrue();
        assertThat(result.document().mimeType())
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument"
                                + ".wordprocessingml.document");
    }

    @Test
    void uploadsPlainTextFormats() {
        assertThat(
                        service.uploadDocument(
                                        OWNER,
                                        command(
                                                "notas.txt",
                                                DocumentFixtures.plainTextBytes("hola mundo")))
                                .created())
                .isTrue();
        assertThat(
                        service.uploadDocument(
                                        OWNER,
                                        command(
                                                "notas.md",
                                                DocumentFixtures.plainTextBytes("# Titulo")))
                                .created())
                .isTrue();
        assertThat(
                        service.uploadDocument(
                                        OWNER,
                                        command(
                                                "datos.csv",
                                                DocumentFixtures.plainTextBytes("a,b\n1,2")))
                                .created())
                .isTrue();
        assertThat(
                        service.uploadDocument(
                                        OWNER,
                                        command(
                                                "datos.json",
                                                DocumentFixtures.plainTextBytes("{\"a\":1}")))
                                .created())
                .isTrue();
    }

    @Test
    void sanitizesTheOriginalFilenameMetadata() {
        UploadDocumentResult result =
                service.uploadDocument(
                        OWNER,
                        command(
                                "../../etc/passwd; rm -rf.txt",
                                DocumentFixtures.plainTextBytes("x")));

        assertThat(result.document().originalFilename()).doesNotContain("/");
    }

    @Test
    void rejectsAnUnsupportedExtension() {
        assertThatThrownBy(
                        () ->
                                service.uploadDocument(
                                        OWNER,
                                        command("script.exe", DocumentFixtures.executableBytes())))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredExtension() {
        assertThatThrownBy(
                        () ->
                                service.uploadDocument(
                                        OWNER,
                                        command("informe.pdf", DocumentFixtures.executableBytes())))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void rejectsFilesLargerThanTheConfiguredLimit() {
        byte[] tooBig = new byte[(int) MAX_UPLOAD_BYTES + 1];
        assertThatThrownBy(() -> service.uploadDocument(OWNER, command("grande.txt", tooBig)))
                .isInstanceOf(DocumentTooLargeException.class);
    }

    @Test
    void rejectsAZipBombDisguisedAsADocx() {
        assertThatThrownBy(
                        () ->
                                service.uploadDocument(
                                        OWNER,
                                        command("bomba.docx", DocumentFixtures.docxZipBombBytes())))
                .isInstanceOf(DocumentTooLargeException.class);
    }

    @Test
    void reuploadingIdenticalContentIsIdempotent() {
        byte[] content = DocumentFixtures.plainTextBytes("contenido identico");

        UploadDocumentResult first = service.uploadDocument(OWNER, command("a.txt", content));
        UploadDocumentResult second = service.uploadDocument(OWNER, command("b.txt", content));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.document().id()).isEqualTo(first.document().id());
    }

    @Test
    void identicalContentFromDifferentOwnersCreatesSeparateDocuments() {
        byte[] content = DocumentFixtures.plainTextBytes("contenido compartido");

        UploadDocumentResult forOwner = service.uploadDocument(OWNER, command("a.txt", content));
        UploadDocumentResult forOther =
                service.uploadDocument(OTHER_OWNER, command("a.txt", content));

        assertThat(forOwner.document().id()).isNotEqualTo(forOther.document().id());
    }

    @Test
    void listGetAndDeleteAreScopedToTheOwner() {
        UploadDocumentResult uploaded =
                service.uploadDocument(OWNER, command("informe.pdf", DocumentFixtures.pdfBytes()));
        UUID documentId = uploaded.document().id();

        DocumentView fetched = service.getDocument(OWNER, documentId);
        assertThat(fetched.id()).isEqualTo(documentId);

        DocumentListView list = service.listDocuments(OWNER, null, 0, 10);
        assertThat(list.items()).extracting(DocumentView::id).contains(documentId);

        assertThatThrownBy(() -> service.getDocument(OTHER_OWNER, documentId))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.deleteDocument(OTHER_OWNER, documentId, "127.0.0.1"))
                .isInstanceOf(DocumentNotFoundException.class);

        service.deleteDocument(OWNER, documentId, "127.0.0.1");
        assertThatThrownBy(() -> service.getDocument(OWNER, documentId))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void deletingRemovesTheStoredFile() {
        UploadDocumentResult uploaded =
                service.uploadDocument(OWNER, command("informe.pdf", DocumentFixtures.pdfBytes()));
        String storageKey =
                documents
                        .findByIdAndOwnerId(uploaded.document().id(), OWNER)
                        .orElseThrow()
                        .storageKey();

        service.deleteDocument(OWNER, uploaded.document().id(), "127.0.0.1");

        assertThatThrownBy(() -> storage.open(OWNER, storageKey))
                .isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void getAndDeleteOfANonExistentDocumentFail() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.getDocument(OWNER, missing))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.deleteDocument(OWNER, missing, "127.0.0.1"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void triggersBackgroundProcessingForANewUploadWithoutBlockingTheResponse() {
        documentProcessing.expectCalls(1);

        UploadDocumentResult result =
                service.uploadDocument(OWNER, command("informe.pdf", DocumentFixtures.pdfBytes()));

        assertThat(result.created()).isTrue();
        assertThat(result.document().status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(documentProcessing.awaitCalls()).isTrue();
        assertThat(documentProcessing.calls())
                .containsExactly(new UUID[] {OWNER, result.document().id()});
    }

    @Test
    void doesNotTriggerProcessingAgainForAnIdempotentDuplicateUpload() {
        documentProcessing.expectCalls(1);
        service.uploadDocument(OWNER, command("informe.pdf", DocumentFixtures.pdfBytes()));
        assertThat(documentProcessing.awaitCalls()).isTrue();

        documentProcessing.expectCalls(1);
        UploadDocumentResult second =
                service.uploadDocument(OWNER, command("informe.pdf", DocumentFixtures.pdfBytes()));

        assertThat(second.created()).isFalse();
        assertThat(documentProcessing.awaitCalls()).isFalse();
        assertThat(documentProcessing.calls()).hasSize(1);
    }

    private UploadDocumentCommand command(String filename, byte[] content) {
        return new UploadDocumentCommand(
                filename, content.length, new ByteArrayInputStream(content), "127.0.0.1");
    }
}
