package com.aidatachat.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.DocumentStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDocumentStorageAdapterTest {

    @TempDir Path tempDir;

    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();

    @Test
    void storesAndOpensTheSameContentRoundTrip() throws IOException {
        FilesystemDocumentStorageAdapter adapter =
                new FilesystemDocumentStorageAdapter(tempDir.toString());

        String storageKey = adapter.store(owner, "informe.pdf", content("contenido real"));

        try (InputStream opened = adapter.open(owner, storageKey)) {
            assertThat(new String(opened.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("contenido real");
        }
    }

    @Test
    void isolatesFilesByOwnerDirectory() {
        FilesystemDocumentStorageAdapter adapter =
                new FilesystemDocumentStorageAdapter(tempDir.toString());
        String storageKey = adapter.store(owner, "informe.pdf", content("secreto"));

        assertThatThrownBy(() -> adapter.open(otherOwner, storageKey))
                .isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void deleteRemovesTheStoredFile() {
        FilesystemDocumentStorageAdapter adapter =
                new FilesystemDocumentStorageAdapter(tempDir.toString());
        String storageKey = adapter.store(owner, "informe.pdf", content("borrar"));

        adapter.delete(owner, storageKey);

        assertThatThrownBy(() -> adapter.open(owner, storageKey))
                .isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void neutralizesPathTraversalSegmentsInStorageKeys() throws IOException {
        FilesystemDocumentStorageAdapter adapter =
                new FilesystemDocumentStorageAdapter(tempDir.toString());
        String storageKey = adapter.store(owner, "informe.pdf", content("contenido real"));

        try (InputStream opened = adapter.open(owner, "../../../etc/" + storageKey)) {
            assertThat(new String(opened.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("contenido real");
        }
    }

    @Test
    void rejectsBlankStorageKeys() {
        FilesystemDocumentStorageAdapter adapter =
                new FilesystemDocumentStorageAdapter(tempDir.toString());

        assertThatThrownBy(() -> adapter.open(owner, "  "))
                .isInstanceOf(DocumentStorageException.class);
    }

    private InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
