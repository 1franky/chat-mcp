package com.aidatachat.application.service;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.exception.ProviderConflictException;
import com.aidatachat.application.exception.ProviderNotFoundException;
import com.aidatachat.application.port.in.ProviderManagementUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.CredentialCipherPort;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.ProviderConnectionRepository;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.EncryptedProviderCredential;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderConnection;
import com.aidatachat.domain.model.ProviderConnectionState;
import com.aidatachat.domain.model.ProviderModel;
import com.aidatachat.domain.model.ProviderModelOrigin;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ProviderManagementService implements ProviderManagementUseCase {

    private static final int MAX_MODELS_PER_SYNC = 1_000;

    private final ProviderConnectionRepository connections;
    private final CredentialCipherPort cipher;
    private final AuditRepository audit;
    private final Map<ProviderType, LlmProviderPort> adapters;
    private final Clock clock;

    public ProviderManagementService(
            ProviderConnectionRepository connections,
            CredentialCipherPort cipher,
            AuditRepository audit,
            List<LlmProviderPort> adapters,
            Clock clock) {
        this.connections = Objects.requireNonNull(connections);
        this.cipher = Objects.requireNonNull(cipher);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.adapters = new EnumMap<>(ProviderType.class);
        for (LlmProviderPort adapter : adapters) {
            if (this.adapters.put(adapter.providerType(), adapter) != null) {
                throw new IllegalStateException(
                        "Duplicate provider adapter: " + adapter.providerType());
            }
        }
    }

    @Override
    public List<ProviderConnectionView> listConnections(UUID ownerId) {
        return connections.findAllByOwnerId(requireOwner(ownerId)).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public ProviderConnectionView createConnection(UUID ownerId, SaveProviderCommand command) {
        UUID validOwner = requireOwner(ownerId);
        ValidatedConfiguration configuration = validate(command, null);
        if (connections.existsByOwnerIdAndDisplayName(validOwner, configuration.displayName())) {
            throw new ProviderConflictException();
        }
        Instant now = clock.instant();
        EncryptedProviderCredential credential = encryptCredential(configuration.apiKey());
        ProviderConnection connection =
                new ProviderConnection(
                        UUID.randomUUID(),
                        validOwner,
                        configuration.displayName(),
                        configuration.providerType(),
                        configuration.baseUrl(),
                        configuration.region(),
                        configuration.modelsPath(),
                        configuration.responsesPath(),
                        configuration.chatCompletionsPath(),
                        configuration.configuredModelId(),
                        configuration.configuredModelId(),
                        credential,
                        ProviderConnectionState.NOT_TESTED,
                        null,
                        null,
                        null,
                        0,
                        now,
                        now);
        ProviderConnection saved = connections.save(connection);
        if (configuration.configuredModelId() != null) {
            connections.replaceConfiguredModel(
                    saved.id(),
                    new ProviderModel(
                            UUID.randomUUID(),
                            saved.id(),
                            configuration.configuredModelId(),
                            configuration.configuredModelId(),
                            ProviderModelOrigin.CONFIGURED,
                            ProviderCapabilityProfile.unknown(),
                            now,
                            null));
        }
        appendAudit(
                validOwner,
                saved.id(),
                "PROVIDER_CONNECTION_CREATED",
                true,
                command.remoteAddress(),
                Map.of("provider_type", saved.providerType().name()));
        return toView(saved);
    }

    @Override
    public ProviderConnectionView updateConnection(
            UUID ownerId, UUID connectionId, SaveProviderCommand command) {
        ProviderConnection current = findOwned(ownerId, connectionId);
        ValidatedConfiguration configuration = validate(command, current);
        if (connections.existsByOwnerIdAndDisplayName(ownerId, configuration.displayName())
                && !current.displayName().equalsIgnoreCase(configuration.displayName())) {
            throw new ProviderConflictException();
        }
        EncryptedProviderCredential credential =
                configuration.apiKey() == null
                        ? current.credential()
                        : encryptCredential(configuration.apiKey());
        Instant now = clock.instant();
        String defaultModelId =
                Objects.equals(current.defaultModelId(), current.configuredModelId())
                        ? configuration.configuredModelId()
                        : current.defaultModelId();
        ProviderConnection updated =
                new ProviderConnection(
                        current.id(),
                        current.ownerId(),
                        configuration.displayName(),
                        current.providerType(),
                        configuration.baseUrl(),
                        configuration.region(),
                        configuration.modelsPath(),
                        configuration.responsesPath(),
                        configuration.chatCompletionsPath(),
                        configuration.configuredModelId(),
                        defaultModelId,
                        credential,
                        ProviderConnectionState.NOT_TESTED,
                        null,
                        null,
                        current.lastModelsSyncedAt(),
                        current.version(),
                        current.createdAt(),
                        now);
        ProviderConnection saved = connections.save(updated);
        ProviderModel configuredModel =
                configuration.configuredModelId() == null
                        ? null
                        : new ProviderModel(
                                UUID.randomUUID(),
                                saved.id(),
                                configuration.configuredModelId(),
                                configuration.configuredModelId(),
                                ProviderModelOrigin.CONFIGURED,
                                ProviderCapabilityProfile.unknown(),
                                now,
                                null);
        connections.replaceConfiguredModel(saved.id(), configuredModel);
        appendAudit(
                ownerId,
                saved.id(),
                "PROVIDER_CONNECTION_UPDATED",
                true,
                command.remoteAddress(),
                Map.of("credential_replaced", Boolean.toString(configuration.apiKey() != null)));
        return toView(saved);
    }

    @Override
    public void deleteConnection(UUID ownerId, UUID connectionId, String remoteAddress) {
        ProviderConnection connection = findOwned(ownerId, connectionId);
        connections.delete(connection);
        appendAudit(
                ownerId,
                connection.id(),
                "PROVIDER_CONNECTION_DELETED",
                true,
                remoteAddress,
                Map.of("provider_type", connection.providerType().name()));
    }

    @Override
    public ProviderTestView testConnection(UUID ownerId, UUID connectionId, String remoteAddress) {
        ProviderConnection connection = findOwned(ownerId, connectionId);
        LlmProviderPort adapter = adapter(connection.providerType());
        ProviderProbeResult result;
        try {
            result =
                    withCredential(
                            connection,
                            credential -> adapter.testConnection(config(connection), credential));
        } catch (ProviderCommunicationException exception) {
            result =
                    ProviderProbeResult.failure(
                            exception.code(),
                            safeMessage(exception.code()),
                            exception.providerRequestId(),
                            exception.retryable());
        }
        Instant testedAt = clock.instant();
        ProviderConnection tested =
                new ProviderConnection(
                        connection.id(),
                        connection.ownerId(),
                        connection.displayName(),
                        connection.providerType(),
                        connection.baseUrl(),
                        connection.region(),
                        connection.modelsPath(),
                        connection.responsesPath(),
                        connection.chatCompletionsPath(),
                        connection.configuredModelId(),
                        connection.defaultModelId(),
                        connection.credential(),
                        result.success()
                                ? ProviderConnectionState.UP
                                : ProviderConnectionState.DOWN,
                        result.success() ? null : result.code(),
                        testedAt,
                        connection.lastModelsSyncedAt(),
                        connection.version(),
                        connection.createdAt(),
                        testedAt);
        connections.save(tested);
        appendAudit(
                ownerId,
                connection.id(),
                "PROVIDER_CONNECTION_TESTED",
                result.success(),
                remoteAddress,
                Map.of("result_code", result.code()));
        return new ProviderTestView(
                result.success(),
                result.code(),
                result.message(),
                result.providerRequestId(),
                result.retryable(),
                testedAt);
    }

    @Override
    public List<ProviderModelView> synchronizeModels(
            UUID ownerId, UUID connectionId, String remoteAddress) {
        ProviderConnection connection = findOwned(ownerId, connectionId);
        LlmProviderPort adapter = adapter(connection.providerType());
        List<DiscoveredProviderModel> discovered =
                withCredential(
                        connection,
                        credential -> adapter.discoverModels(config(connection), credential));
        if (discovered.size() > MAX_MODELS_PER_SYNC) {
            throw new ProviderCommunicationException("MODEL_CATALOG_TOO_LARGE", null, false, null);
        }
        Instant synchronizedAt = clock.instant();
        Set<String> identifiers = new HashSet<>();
        List<ProviderModel> models = new ArrayList<>();
        for (DiscoveredProviderModel discoveredModel : discovered) {
            String modelId = validateModelId(discoveredModel.modelId());
            if (!identifiers.add(modelId)) {
                continue;
            }
            models.add(
                    new ProviderModel(
                            UUID.randomUUID(),
                            connection.id(),
                            modelId,
                            safeDisplayName(discoveredModel.displayName(), modelId),
                            ProviderModelOrigin.DISCOVERED,
                            discoveredModel.capabilities(),
                            synchronizedAt,
                            synchronizedAt));
        }
        connections.replaceDiscoveredModels(connection.id(), models, synchronizedAt);
        ProviderConnection synchronizedConnection =
                copyWithModelsSynchronized(connection, synchronizedAt);
        connections.save(synchronizedConnection);
        appendAudit(
                ownerId,
                connection.id(),
                "PROVIDER_MODELS_SYNCHRONIZED",
                true,
                remoteAddress,
                Map.of("model_count", Integer.toString(models.size())));
        return listModels(ownerId, connectionId);
    }

    @Override
    public List<ProviderModelView> listModels(UUID ownerId, UUID connectionId) {
        ProviderConnection connection = findOwned(ownerId, connectionId);
        return connections.findModels(connection.id()).stream()
                .sorted(Comparator.comparing(ProviderModel::modelId))
                .map(this::toView)
                .toList();
    }

    @Override
    public ProviderModelView addManualModel(
            UUID ownerId, UUID connectionId, String modelId, String remoteAddress) {
        ProviderConnection connection = findOwned(ownerId, connectionId);
        String validModelId = validateModelId(modelId);
        ProviderProbeResult validation =
                withCredential(
                        connection,
                        credential ->
                                adapter(connection.providerType())
                                        .validateModel(
                                                config(connection), credential, validModelId));
        if (!validation.success()) {
            throw new IllegalArgumentException("Model validation failed");
        }
        Instant now = clock.instant();
        ProviderModel model =
                connections
                        .findModel(connection.id(), validModelId)
                        .map(
                                current ->
                                        new ProviderModel(
                                                current.id(),
                                                current.providerConnectionId(),
                                                current.modelId(),
                                                current.displayName(),
                                                ProviderModelOrigin.MANUAL,
                                                current.capabilities(),
                                                current.discoveredAt(),
                                                now))
                        .orElseGet(
                                () ->
                                        new ProviderModel(
                                                UUID.randomUUID(),
                                                connection.id(),
                                                validModelId,
                                                validModelId,
                                                ProviderModelOrigin.MANUAL,
                                                ProviderCapabilityProfile.unknown(),
                                                now,
                                                now));
        ProviderModel saved = connections.saveModel(model);
        appendAudit(
                ownerId,
                connection.id(),
                "PROVIDER_MODEL_ADDED",
                true,
                remoteAddress,
                Map.of("model_id_hash", Integer.toHexString(validModelId.hashCode())));
        return toView(saved);
    }

    @Override
    public ProviderConnectionView selectDefaultModel(
            UUID ownerId, UUID connectionId, String modelId) {
        ProviderConnection connection = findOwned(ownerId, connectionId);
        String validModelId = validateModelId(modelId);
        connections
                .findModel(connection.id(), validModelId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown model"));
        Instant now = clock.instant();
        ProviderConnection updated =
                new ProviderConnection(
                        connection.id(),
                        connection.ownerId(),
                        connection.displayName(),
                        connection.providerType(),
                        connection.baseUrl(),
                        connection.region(),
                        connection.modelsPath(),
                        connection.responsesPath(),
                        connection.chatCompletionsPath(),
                        connection.configuredModelId(),
                        validModelId,
                        connection.credential(),
                        connection.state(),
                        connection.lastErrorCode(),
                        connection.lastTestedAt(),
                        connection.lastModelsSyncedAt(),
                        connection.version(),
                        connection.createdAt(),
                        now);
        return toView(connections.save(updated));
    }

    private ValidatedConfiguration validate(
            SaveProviderCommand command, ProviderConnection current) {
        Objects.requireNonNull(command, "command is required");
        String displayName = required(command.displayName(), "displayName", 2, 120);
        ProviderType type =
                Objects.requireNonNull(command.providerType(), "providerType is required");
        adapter(type);
        if (current != null && current.providerType() != type) {
            throw new IllegalArgumentException("Provider type cannot be changed");
        }
        String apiKey = optionalSecret(command.apiKey());
        if (current == null && type.requiresCredential() && apiKey == null) {
            throw new IllegalArgumentException("API key is required");
        }
        String baseUrl = optional(command.baseUrl(), 2_048);
        if (type.requiresConfigurableBaseUrl()) {
            validateBaseUrl(baseUrl);
        } else if (baseUrl != null) {
            throw new IllegalArgumentException("Base URL is fixed for this provider");
        }
        String region = optional(command.region(), 64);
        String configuredModelId = optionalModelId(command.configuredModelId());
        if (type == ProviderType.BYTEPLUS && (region == null || configuredModelId == null)) {
            throw new IllegalArgumentException("BytePlus region and model ID are required");
        }
        if (type == ProviderType.OPENAI_COMPATIBLE
                && command.modelsPath() == null
                && configuredModelId == null) {
            throw new IllegalArgumentException(
                    "Compatible providers require modelsPath or configuredModelId");
        }
        return new ValidatedConfiguration(
                displayName,
                type,
                apiKey,
                baseUrl,
                region,
                optionalPath(command.modelsPath()),
                optionalPath(command.responsesPath()),
                optionalPath(command.chatCompletionsPath()),
                configuredModelId);
    }

    private ProviderConnection findOwned(UUID ownerId, UUID connectionId) {
        return connections
                .findByIdAndOwnerId(
                        Objects.requireNonNull(connectionId, "connectionId is required"),
                        requireOwner(ownerId))
                .orElseThrow(ProviderNotFoundException::new);
    }

    private LlmProviderPort adapter(ProviderType type) {
        LlmProviderPort adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("Provider type is not enabled");
        }
        return adapter;
    }

    private ProviderConnectionView toView(ProviderConnection connection) {
        return new ProviderConnectionView(
                connection.id(),
                connection.displayName(),
                connection.providerType(),
                connection.baseUrl(),
                connection.region(),
                connection.modelsPath(),
                connection.responsesPath(),
                connection.chatCompletionsPath(),
                connection.configuredModelId(),
                connection.defaultModelId(),
                connection.credential() == null ? null : connection.credential().maskedValue(),
                connection.state(),
                connection.lastErrorCode(),
                connection.lastTestedAt(),
                connection.lastModelsSyncedAt(),
                adapter(connection.providerType()).capabilities(config(connection)),
                connection.createdAt(),
                connection.updatedAt());
    }

    private ProviderModelView toView(ProviderModel model) {
        return new ProviderModelView(
                model.id(),
                model.modelId(),
                model.displayName(),
                model.origin(),
                model.capabilities(),
                model.discoveredAt(),
                model.lastValidatedAt());
    }

    private LlmProviderPort.ProviderClientConfiguration config(ProviderConnection connection) {
        return new LlmProviderPort.ProviderClientConfiguration(
                connection.baseUrl(),
                connection.region(),
                connection.modelsPath(),
                connection.responsesPath(),
                connection.chatCompletionsPath(),
                connection.configuredModelId());
    }

    private EncryptedProviderCredential encryptCredential(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        char[] plaintext = apiKey.toCharArray();
        try {
            CredentialCipherPort.EncryptedCredential encrypted = cipher.encrypt(plaintext);
            return new EncryptedProviderCredential(
                    encrypted.ciphertext(),
                    encrypted.nonce(),
                    encrypted.keyVersion(),
                    mask(apiKey));
        } finally {
            Arrays.fill(plaintext, '\0');
        }
    }

    private <T> T withCredential(ProviderConnection connection, CredentialOperation<T> operation) {
        char[] plaintext =
                connection.credential() == null
                        ? new char[0]
                        : cipher.decrypt(
                                new CredentialCipherPort.EncryptedCredential(
                                        connection.credential().ciphertext(),
                                        connection.credential().nonce(),
                                        connection.credential().keyVersion()));
        try {
            return operation.execute(plaintext);
        } finally {
            Arrays.fill(plaintext, '\0');
        }
    }

    private ProviderConnection copyWithModelsSynchronized(
            ProviderConnection connection, Instant synchronizedAt) {
        return new ProviderConnection(
                connection.id(),
                connection.ownerId(),
                connection.displayName(),
                connection.providerType(),
                connection.baseUrl(),
                connection.region(),
                connection.modelsPath(),
                connection.responsesPath(),
                connection.chatCompletionsPath(),
                connection.configuredModelId(),
                connection.defaultModelId(),
                connection.credential(),
                connection.state(),
                connection.lastErrorCode(),
                connection.lastTestedAt(),
                synchronizedAt,
                connection.version(),
                connection.createdAt(),
                synchronizedAt);
    }

    private void appendAudit(
            UUID actorId,
            UUID targetId,
            String eventType,
            boolean success,
            String remoteAddress,
            Map<String, String> metadata) {
        audit.append(
                new AuditRepository.AuditEvent(
                        actorId,
                        targetId,
                        eventType,
                        success,
                        clock.instant(),
                        remoteAddress,
                        metadata));
    }

    private String safeMessage(String code) {
        return switch (code) {
            case "PROVIDER_AUTHENTICATION_FAILED" ->
                    "El proveedor rechazo la credencial configurada.";
            case "PROVIDER_RATE_LIMITED" ->
                    "El proveedor aplico un limite temporal. Intenta mas tarde.";
            case "PROVIDER_TIMEOUT" -> "El proveedor no respondio dentro del tiempo permitido.";
            case "PROVIDER_DESTINATION_BLOCKED" ->
                    "El destino no esta autorizado por la politica del servidor.";
            default -> "No fue posible verificar la conexion con el proveedor.";
        };
    }

    private String mask(String secret) {
        int visible = Math.min(4, secret.length());
        return "••••" + secret.substring(secret.length() - visible);
    }

    private String required(String value, String field, int min, int max) {
        String result = optional(value, max);
        if (result == null || result.length() < min) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return result;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (result.length() > max || result.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Field is invalid");
        }
        return result;
    }

    private String optionalSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 8_192 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Credential is invalid");
        }
        return value;
    }

    private String optionalModelId(String modelId) {
        return modelId == null || modelId.isBlank() ? null : validateModelId(modelId);
    }

    private String validateModelId(String modelId) {
        String valid = required(modelId, "modelId", 1, 255);
        if (valid.contains("/") && valid.startsWith("/")) {
            throw new IllegalArgumentException("modelId is invalid");
        }
        return valid;
    }

    private String safeDisplayName(String displayName, String fallback) {
        String valid = optional(displayName, 255);
        return valid == null ? fallback : valid;
    }

    private String optionalPath(String path) {
        String valid = optional(path, 255);
        if (valid == null) {
            return null;
        }
        if (!valid.startsWith("/")
                || valid.contains("..")
                || valid.contains("\\")
                || valid.contains("?")
                || valid.contains("#")) {
            throw new IllegalArgumentException("Provider path is invalid");
        }
        return valid;
    }

    private void validateBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("baseUrl is invalid", exception);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("baseUrl is invalid");
        }
    }

    private UUID requireOwner(UUID ownerId) {
        return Objects.requireNonNull(ownerId, "ownerId is required");
    }

    @FunctionalInterface
    private interface CredentialOperation<T> {
        T execute(char[] credential);
    }

    private record ValidatedConfiguration(
            String displayName,
            ProviderType providerType,
            String apiKey,
            String baseUrl,
            String region,
            String modelsPath,
            String responsesPath,
            String chatCompletionsPath,
            String configuredModelId) {}
}
