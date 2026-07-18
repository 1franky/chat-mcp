package com.aidatachat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentChunkTest {

    private final UUID id = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @Test
    void acceptsAValidChunk() {
        DocumentChunk chunk =
                new DocumentChunk(
                        id,
                        documentId,
                        ownerId,
                        0,
                        "contenido",
                        3,
                        "Introduccion",
                        "fake-embedding-v1",
                        new float[] {1f, 0f},
                        now);

        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.pageNumber()).isEqualTo(3);
    }

    @Test
    void acceptsNullPageNumberAndSectionLabel() {
        DocumentChunk chunk =
                new DocumentChunk(
                        id,
                        documentId,
                        ownerId,
                        0,
                        "contenido",
                        null,
                        null,
                        "fake-embedding-v1",
                        new float[] {1f},
                        now);

        assertThat(chunk.pageNumber()).isNull();
        assertThat(chunk.sectionLabel()).isNull();
    }

    @Test
    void rejectsNegativeChunkIndex() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.chunkIndex = -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.content = ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsContentLongerThan8000Characters() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.content = "a".repeat(8001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositivePageNumber() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.pageNumber = 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSectionLabel() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.sectionLabel = "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankEmbeddingModelId() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.embeddingModelId = " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyEmbedding() {
        assertThatThrownBy(() -> chunkWith(builder -> builder.embedding = new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void chunkWith(java.util.function.Consumer<Builder> customize) {
        Builder builder = new Builder();
        customize.accept(builder);
        new DocumentChunk(
                id,
                documentId,
                ownerId,
                builder.chunkIndex,
                builder.content,
                builder.pageNumber,
                builder.sectionLabel,
                builder.embeddingModelId,
                builder.embedding,
                now);
    }

    private static final class Builder {
        int chunkIndex = 0;
        String content = "contenido";
        Integer pageNumber = null;
        String sectionLabel = null;
        String embeddingModelId = "fake-embedding-v1";
        float[] embedding = new float[] {1f};
    }
}
