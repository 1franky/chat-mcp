package com.aidatachat.domain.model;

import java.util.List;
import java.util.Objects;

public record LlmChunk(
        String content,
        boolean finished,
        Integer inputTokens,
        Integer outputTokens,
        String finishReason,
        String providerRequestId,
        List<LlmToolCallDelta> toolCalls) {

    public LlmChunk {
        Objects.requireNonNull(content, "content is required");
        if (inputTokens != null && inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens cannot be negative");
        }
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens cannot be negative");
        }
        toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls is required"));
    }

    public LlmChunk(String content, boolean finished) {
        this(content, finished, null, null, null, null, List.of());
    }

    public LlmChunk(
            String content,
            boolean finished,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId) {
        this(
                content,
                finished,
                inputTokens,
                outputTokens,
                finishReason,
                providerRequestId,
                List.of());
    }

    public static LlmChunk delta(String content, String providerRequestId) {
        return new LlmChunk(content, false, null, null, null, providerRequestId, List.of());
    }

    public static LlmChunk completed(
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId) {
        return new LlmChunk(
                "", true, inputTokens, outputTokens, finishReason, providerRequestId, List.of());
    }

    public static LlmChunk toolCallDelta(
            List<LlmToolCallDelta> toolCalls, String providerRequestId) {
        return new LlmChunk("", false, null, null, null, providerRequestId, toolCalls);
    }
}
