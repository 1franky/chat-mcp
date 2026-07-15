package com.aidatachat.domain.model;

import java.util.Objects;

public record LlmChunk(String content, boolean finished) {

    public LlmChunk {
        Objects.requireNonNull(content, "content is required");
    }
}
