package com.aidatachat.application.port.out;

import java.util.List;

public interface EmbeddingProviderPort {

    EmbeddingBatch embed(String modelId, List<String> inputs);

    record EmbeddingBatch(int dimension, List<float[]> vectors) {

        public EmbeddingBatch {
            vectors = List.copyOf(vectors);
            for (float[] vector : vectors) {
                if (vector.length != dimension) {
                    throw new IllegalArgumentException(
                            "Embedding vector length does not match the declared dimension");
                }
            }
        }
    }
}
