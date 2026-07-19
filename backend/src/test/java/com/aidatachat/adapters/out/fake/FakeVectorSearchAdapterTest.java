package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidatachat.application.port.out.VectorSearchPort.ChunkRecord;
import com.aidatachat.application.port.out.VectorSearchPort.VectorMatch;
import com.aidatachat.application.port.out.VectorSearchPort.VectorRecord;
import com.aidatachat.domain.model.DocumentChunk;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeVectorSearchAdapterTest {

    private final FakeVectorSearchAdapter adapter = new FakeVectorSearchAdapter();
    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();
    private final UUID document = UUID.randomUUID();

    @Test
    void ranksTheClosestVectorFirst() {
        UUID closeChunk = UUID.randomUUID();
        UUID farChunk = UUID.randomUUID();
        adapter.index(
                owner,
                document,
                List.of(
                        new VectorRecord(closeChunk, new float[] {1f, 0f, 0f}),
                        new VectorRecord(farChunk, new float[] {0f, 1f, 0f})));

        List<VectorMatch> matches =
                adapter.search(owner, List.of(document), new float[] {1f, 0f, 0f}, 2);

        assertThat(matches).hasSize(2);
        assertThat(matches.getFirst().chunkId()).isEqualTo(closeChunk);
        assertThat(matches.getFirst().score()).isGreaterThan(matches.getLast().score());
    }

    @Test
    void limitsResultsToTopK() {
        adapter.index(
                owner,
                document,
                List.of(
                        new VectorRecord(UUID.randomUUID(), new float[] {1f, 0f}),
                        new VectorRecord(UUID.randomUUID(), new float[] {0f, 1f}),
                        new VectorRecord(UUID.randomUUID(), new float[] {1f, 1f})));

        assertThat(adapter.search(owner, List.of(document), new float[] {1f, 0f}, 1)).hasSize(1);
    }

    @Test
    void isolatesSearchResultsByOwner() {
        adapter.index(
                owner,
                document,
                List.of(new VectorRecord(UUID.randomUUID(), new float[] {1f, 0f})));

        assertThat(adapter.search(otherOwner, List.of(document), new float[] {1f, 0f}, 10))
                .isEmpty();
    }

    @Test
    void restrictsSearchResultsToTheRequestedDocuments() {
        UUID otherDocument = UUID.randomUUID();
        UUID inScopeChunk = UUID.randomUUID();
        UUID outOfScopeChunk = UUID.randomUUID();
        adapter.index(
                owner, document, List.of(new VectorRecord(inScopeChunk, new float[] {1f, 0f})));
        adapter.index(
                owner,
                otherDocument,
                List.of(new VectorRecord(outOfScopeChunk, new float[] {1f, 0f})));

        List<VectorMatch> matches =
                adapter.search(owner, List.of(document), new float[] {1f, 0f}, 10);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().chunkId()).isEqualTo(inScopeChunk);
    }

    @Test
    void deleteByDocumentRemovesOnlyThatDocumentsChunks() {
        UUID otherDocument = UUID.randomUUID();
        UUID keptChunk = UUID.randomUUID();
        adapter.index(
                owner,
                document,
                List.of(new VectorRecord(UUID.randomUUID(), new float[] {1f, 0f})));
        adapter.index(
                owner, otherDocument, List.of(new VectorRecord(keptChunk, new float[] {0f, 1f})));

        adapter.deleteByDocument(owner, document);

        List<VectorMatch> remaining =
                adapter.search(owner, List.of(document, otherDocument), new float[] {0f, 1f}, 10);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().chunkId()).isEqualTo(keptChunk);
    }

    @Test
    void replaceChunksOverwritesTheDocumentsEntireChunkSet() {
        UUID staleChunk = UUID.randomUUID();
        adapter.replaceChunks(
                owner, document, List.of(chunkRecord(staleChunk, 0, new float[] {1f, 0f})));

        UUID freshChunk = UUID.randomUUID();
        adapter.replaceChunks(
                owner, document, List.of(chunkRecord(freshChunk, 0, new float[] {0f, 1f})));

        List<VectorMatch> matches =
                adapter.search(owner, List.of(document), new float[] {0f, 1f}, 10);
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().chunkId()).isEqualTo(freshChunk);
    }

    @Test
    void replaceChunksDoesNotAffectOtherDocuments() {
        UUID otherDocument = UUID.randomUUID();
        UUID keptChunk = UUID.randomUUID();
        adapter.replaceChunks(
                owner, otherDocument, List.of(chunkRecord(keptChunk, 0, new float[] {0f, 1f})));

        adapter.replaceChunks(
                owner, document, List.of(chunkRecord(UUID.randomUUID(), 0, new float[] {1f, 0f})));

        List<UUID> chunkIds =
                adapter.search(owner, List.of(otherDocument), new float[] {0f, 1f}, 10).stream()
                        .map(VectorMatch::chunkId)
                        .toList();
        assertThat(chunkIds).contains(keptChunk);
    }

    @Test
    void findByIdsResolvesFullChunkContentScopedToOwner() {
        UUID chunkId = UUID.randomUUID();
        adapter.replaceChunks(
                owner,
                document,
                List.of(
                        new ChunkRecord(
                                chunkId,
                                2,
                                "contenido real",
                                5,
                                "Introduccion",
                                "fake-embedding-v1",
                                new float[] {1f, 0f},
                                Instant.now())));

        List<DocumentChunk> found = adapter.findByIds(owner, List.of(chunkId));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("contenido real");
        assertThat(found.getFirst().pageNumber()).isEqualTo(5);
        assertThat(found.getFirst().sectionLabel()).isEqualTo("Introduccion");
        assertThat(adapter.findByIds(otherOwner, List.of(chunkId))).isEmpty();
    }

    private ChunkRecord chunkRecord(UUID chunkId, int chunkIndex, float[] embedding) {
        return new ChunkRecord(
                chunkId,
                chunkIndex,
                "contenido",
                null,
                null,
                "fake-embedding-v1",
                embedding,
                Instant.now());
    }
}
