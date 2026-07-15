package com.aidatachat.domain.model;

import java.util.Objects;

public record ProviderCapabilityProfile(
        CapabilityAvailability chat,
        CapabilityAvailability streaming,
        CapabilityAvailability toolCalling,
        CapabilityAvailability structuredOutput,
        CapabilityAvailability vision,
        CapabilityAvailability embeddings,
        CapabilityAvailability modelDiscovery) {

    public ProviderCapabilityProfile {
        Objects.requireNonNull(chat, "chat is required");
        Objects.requireNonNull(streaming, "streaming is required");
        Objects.requireNonNull(toolCalling, "toolCalling is required");
        Objects.requireNonNull(structuredOutput, "structuredOutput is required");
        Objects.requireNonNull(vision, "vision is required");
        Objects.requireNonNull(embeddings, "embeddings is required");
        Objects.requireNonNull(modelDiscovery, "modelDiscovery is required");
    }

    public static ProviderCapabilityProfile unknown() {
        return new ProviderCapabilityProfile(
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN,
                CapabilityAvailability.UNKNOWN);
    }
}
