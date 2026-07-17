package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.port.out.EmbeddingProviderPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class FakeEmbeddingProviderAdapter implements EmbeddingProviderPort {

    public static final String MODEL_ID = "fake-embedding-v1";
    public static final int DIMENSION = 1536;

    @Override
    public EmbeddingBatch embed(String modelId, List<String> inputs) {
        Objects.requireNonNull(inputs, "inputs is required");
        if (!MODEL_ID.equals(modelId)) {
            throw new IllegalArgumentException("Unknown fake embedding model: " + modelId);
        }
        List<float[]> vectors = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            vectors.add(
                    deterministicUnitVector(Objects.requireNonNull(input, "input is required")));
        }
        return new EmbeddingBatch(DIMENSION, vectors);
    }

    private static float[] deterministicUnitVector(String input) {
        Random random = new Random(seed(input));
        float[] vector = new float[DIMENSION];
        double sumOfSquares = 0;
        for (int i = 0; i < DIMENSION; i++) {
            double component = random.nextGaussian();
            vector[i] = (float) component;
            sumOfSquares += component * component;
        }
        double norm = Math.sqrt(sumOfSquares);
        for (int i = 0; i < DIMENSION; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    private static long seed(String input) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < Long.BYTES; i++) {
                seed = (seed << 8) | (digest[i] & 0xFF);
            }
            return seed;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required and always available", exception);
        }
    }
}
