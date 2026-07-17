package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidatachat.application.port.out.VectorSearchPort.VectorMatch;
import com.aidatachat.application.port.out.VectorSearchPort.VectorRecord;
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

        List<VectorMatch> matches = adapter.search(owner, new float[] {1f, 0f, 0f}, 2);

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

        assertThat(adapter.search(owner, new float[] {1f, 0f}, 1)).hasSize(1);
    }

    @Test
    void isolatesSearchResultsByOwner() {
        adapter.index(
                owner,
                document,
                List.of(new VectorRecord(UUID.randomUUID(), new float[] {1f, 0f})));

        assertThat(adapter.search(otherOwner, new float[] {1f, 0f}, 10)).isEmpty();
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

        List<VectorMatch> remaining = adapter.search(owner, new float[] {0f, 1f}, 10);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().chunkId()).isEqualTo(keptChunk);
    }
}
