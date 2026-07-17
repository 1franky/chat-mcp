package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import com.aidatachat.application.port.out.EmbeddingProviderPort.EmbeddingBatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeEmbeddingProviderAdapterTest {

    private final FakeEmbeddingProviderAdapter adapter = new FakeEmbeddingProviderAdapter();

    @Test
    void embedsEachInputIntoAUnitVectorOfTheDeclaredDimension() {
        EmbeddingBatch batch =
                adapter.embed(
                        FakeEmbeddingProviderAdapter.MODEL_ID, List.of("hola mundo", "otro texto"));

        assertThat(batch.dimension()).isEqualTo(FakeEmbeddingProviderAdapter.DIMENSION);
        assertThat(batch.vectors()).hasSize(2);
        for (float[] vector : batch.vectors()) {
            assertThat(vector).hasSize(FakeEmbeddingProviderAdapter.DIMENSION);
            assertThat(magnitude(vector)).isCloseTo(1.0, offset(1e-6));
        }
    }

    @Test
    void isDeterministicForTheSameInputAndDifferentForDifferentInput() {
        EmbeddingBatch first =
                adapter.embed(FakeEmbeddingProviderAdapter.MODEL_ID, List.of("mismo texto"));
        EmbeddingBatch second =
                adapter.embed(FakeEmbeddingProviderAdapter.MODEL_ID, List.of("mismo texto"));
        EmbeddingBatch third =
                adapter.embed(FakeEmbeddingProviderAdapter.MODEL_ID, List.of("texto distinto"));

        assertThat(first.vectors().getFirst()).isEqualTo(second.vectors().getFirst());
        assertThat(first.vectors().getFirst()).isNotEqualTo(third.vectors().getFirst());
    }

    @Test
    void rejectsUnknownModels() {
        assertThatThrownBy(() -> adapter.embed("not-configured", List.of("hola")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown fake embedding model");
    }

    @Test
    void rejectsAVectorLengthMismatchedWithItsDeclaredDimension() {
        assertThatThrownBy(() -> new EmbeddingBatch(3, List.of(new float[] {1f, 2f})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double magnitude(float[] vector) {
        double sumOfSquares = 0;
        for (float component : vector) {
            sumOfSquares += (double) component * component;
        }
        return Math.sqrt(sumOfSquares);
    }
}
