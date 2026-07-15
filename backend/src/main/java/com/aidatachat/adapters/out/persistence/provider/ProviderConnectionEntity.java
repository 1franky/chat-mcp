package com.aidatachat.adapters.out.persistence.provider;

import com.aidatachat.domain.model.EncryptedProviderCredential;
import com.aidatachat.domain.model.ProviderConnection;
import com.aidatachat.domain.model.ProviderConnectionState;
import com.aidatachat.domain.model.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_connection", schema = "chat")
class ProviderConnectionEntity {

    @Id private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "base_url", length = 2048)
    private String baseUrl;

    @Column(length = 64)
    private String region;

    @Column(name = "models_path", length = 255)
    private String modelsPath;

    @Column(name = "responses_path", length = 255)
    private String responsesPath;

    @Column(name = "chat_completions_path", length = 255)
    private String chatCompletionsPath;

    @Column(name = "configured_model_id", length = 255)
    private String configuredModelId;

    @Column(name = "default_model_id", length = 255)
    private String defaultModelId;

    @Column(name = "credential_ciphertext")
    private byte[] credentialCiphertext;

    @Column(name = "credential_nonce")
    private byte[] credentialNonce;

    @Column(name = "credential_key_version")
    private Integer credentialKeyVersion;

    @Column(name = "credential_masked", length = 32)
    private String credentialMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProviderConnectionState state;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_models_synced_at")
    private Instant lastModelsSyncedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderConnectionEntity() {}

    ProviderConnectionEntity(ProviderConnection connection) {
        this.id = connection.id();
        this.ownerId = connection.ownerId();
        this.createdAt = connection.createdAt();
        update(connection);
    }

    void update(ProviderConnection connection) {
        this.displayName = connection.displayName();
        this.providerType = connection.providerType();
        this.baseUrl = connection.baseUrl();
        this.region = connection.region();
        this.modelsPath = connection.modelsPath();
        this.responsesPath = connection.responsesPath();
        this.chatCompletionsPath = connection.chatCompletionsPath();
        this.configuredModelId = connection.configuredModelId();
        this.defaultModelId = connection.defaultModelId();
        EncryptedProviderCredential credential = connection.credential();
        this.credentialCiphertext = credential == null ? null : credential.ciphertext();
        this.credentialNonce = credential == null ? null : credential.nonce();
        this.credentialKeyVersion = credential == null ? null : credential.keyVersion();
        this.credentialMasked = credential == null ? null : credential.maskedValue();
        this.state = connection.state();
        this.lastErrorCode = connection.lastErrorCode();
        this.lastTestedAt = connection.lastTestedAt();
        this.lastModelsSyncedAt = connection.lastModelsSyncedAt();
        this.updatedAt = connection.updatedAt();
    }

    ProviderConnection toDomain() {
        EncryptedProviderCredential credential =
                credentialCiphertext == null
                        ? null
                        : new EncryptedProviderCredential(
                                credentialCiphertext,
                                credentialNonce,
                                credentialKeyVersion,
                                credentialMasked);
        return new ProviderConnection(
                id,
                ownerId,
                displayName,
                providerType,
                baseUrl,
                region,
                modelsPath,
                responsesPath,
                chatCompletionsPath,
                configuredModelId,
                defaultModelId,
                credential,
                state,
                lastErrorCode,
                lastTestedAt,
                lastModelsSyncedAt,
                version,
                createdAt,
                updatedAt);
    }
}
