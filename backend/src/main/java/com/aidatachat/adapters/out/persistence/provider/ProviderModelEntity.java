package com.aidatachat.adapters.out.persistence.provider;

import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderModel;
import com.aidatachat.domain.model.ProviderModelOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_model", schema = "chat")
class ProviderModelEntity {

    @Id private UUID id;

    @Column(name = "provider_connection_id", nullable = false)
    private UUID providerConnectionId;

    @Column(name = "model_id", nullable = false, length = 255)
    private String modelId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProviderModelOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_capability", nullable = false, length = 16)
    private CapabilityAvailability chatCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "streaming_capability", nullable = false, length = 16)
    private CapabilityAvailability streamingCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_calling_capability", nullable = false, length = 16)
    private CapabilityAvailability toolCallingCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "structured_output_capability", nullable = false, length = 16)
    private CapabilityAvailability structuredOutputCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "vision_capability", nullable = false, length = 16)
    private CapabilityAvailability visionCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "embeddings_capability", nullable = false, length = 16)
    private CapabilityAvailability embeddingsCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_discovery_capability", nullable = false, length = 16)
    private CapabilityAvailability modelDiscoveryCapability;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    protected ProviderModelEntity() {}

    ProviderModelEntity(ProviderModel model) {
        this.id = model.id();
        update(model);
    }

    void update(ProviderModel model) {
        this.providerConnectionId = model.providerConnectionId();
        this.modelId = model.modelId();
        this.displayName = model.displayName();
        this.origin = model.origin();
        ProviderCapabilityProfile capabilities = model.capabilities();
        this.chatCapability = capabilities.chat();
        this.streamingCapability = capabilities.streaming();
        this.toolCallingCapability = capabilities.toolCalling();
        this.structuredOutputCapability = capabilities.structuredOutput();
        this.visionCapability = capabilities.vision();
        this.embeddingsCapability = capabilities.embeddings();
        this.modelDiscoveryCapability = capabilities.modelDiscovery();
        this.discoveredAt = model.discoveredAt();
        this.lastValidatedAt = model.lastValidatedAt();
    }

    ProviderModel toDomain() {
        return new ProviderModel(
                id,
                providerConnectionId,
                modelId,
                displayName,
                origin,
                new ProviderCapabilityProfile(
                        chatCapability,
                        streamingCapability,
                        toolCallingCapability,
                        structuredOutputCapability,
                        visionCapability,
                        embeddingsCapability,
                        modelDiscoveryCapability),
                discoveredAt,
                lastValidatedAt);
    }
}
