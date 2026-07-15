package com.aidatachat.domain.model;

public enum ProviderType {
    OPENAI,
    ANTHROPIC,
    BYTEPLUS,
    OPENAI_COMPATIBLE,
    OLLAMA,
    FAKE;

    public boolean requiresCredential() {
        return this != OLLAMA && this != FAKE;
    }

    public boolean requiresConfigurableBaseUrl() {
        return this == OPENAI_COMPATIBLE || this == OLLAMA;
    }
}
