package com.aidatachat.domain.model;

import java.util.Objects;

/**
 * Incremental fragment of a provider tool call emitted during streaming. {@code
 * providerToolCallId}/{@code toolName} are only present on the delta that introduces the call;
 * {@code argumentsJsonDelta} accumulates into the full JSON arguments text across deltas sharing
 * the same {@code index}.
 */
public record LlmToolCallDelta(
        int index, String providerToolCallId, String toolName, String argumentsJsonDelta) {

    public LlmToolCallDelta {
        argumentsJsonDelta = Objects.requireNonNullElse(argumentsJsonDelta, "");
    }
}
