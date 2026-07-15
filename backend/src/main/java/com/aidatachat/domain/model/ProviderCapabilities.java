package com.aidatachat.domain.model;

public record ProviderCapabilities(
        boolean chat,
        boolean streaming,
        boolean toolCalling,
        boolean structuredOutput,
        boolean vision,
        boolean embeddings,
        boolean modelDiscovery) {}
