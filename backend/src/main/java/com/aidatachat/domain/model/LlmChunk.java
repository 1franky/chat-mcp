package com.aidatachat.domain.model;

import java.util.Objects;

public record LlmChunk(
        String content,
        boolean finished,
        Integer inputTokens,
        Integer outputTokens,
        String finishReason,
        String providerRequestId) {

    public LlmChunk {
        Objects.requireNonNull(content, "content is required");
        if (inputTokens != null && inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens cannot be negative");
        }
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens cannot be negative");
        }
    }

    public LlmChunk(String content, boolean finished) {
        this(content, finished, null, null, null, null);
    }

    public static LlmChunk delta(String content, String providerRequestId) {
        return new LlmChunk(content, false, null, null, null, providerRequestId);
    }

    public static LlmChunk completed(
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId) {
        return new LlmChunk("", true, inputTokens, outputTokens, finishReason, providerRequestId);
    }
}
