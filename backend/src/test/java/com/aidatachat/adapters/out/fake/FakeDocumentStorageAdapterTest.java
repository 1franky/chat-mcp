package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.DocumentStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeDocumentStorageAdapterTest {

    private final FakeDocumentStorageAdapter adapter = new FakeDocumentStorageAdapter();
    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();

    @Test
    void storesAndOpensTheSameContentRoundTrip() throws IOException {
        String storageKey = adapter.store(owner, "doc.txt", content("hola mundo"));

        try (InputStream opened = adapter.open(owner, storageKey)) {
            assertThat(new String(opened.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("hola mundo");
        }
    }

    @Test
    void isolatesStorageByOwner() {
        String storageKey = adapter.store(owner, "doc.txt", content("secreto"));

        assertThatThrownBy(() -> adapter.open(otherOwner, storageKey))
                .isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void deleteRemovesTheStoredContent() {
        String storageKey = adapter.store(owner, "doc.txt", content("borrar"));

        adapter.delete(owner, storageKey);

        assertThatThrownBy(() -> adapter.open(owner, storageKey))
                .isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void openingAMissingKeyFails() {
        assertThatThrownBy(() -> adapter.open(owner, "does-not-exist"))
                .isInstanceOf(DocumentStorageException.class);
    }

    private InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
