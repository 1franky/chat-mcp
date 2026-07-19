package com.aidatachat.configuration;

import com.aidatachat.adapters.out.extraction.DocumentTextExtractorAdapter;
import com.aidatachat.adapters.out.fake.FakeDocumentRepository;
import com.aidatachat.adapters.out.fake.FakeDocumentStorageAdapter;
import com.aidatachat.adapters.out.fake.FakeEmbeddingProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeLlmProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeMcpGateway;
import com.aidatachat.adapters.out.fake.FakeVectorSearchAdapter;
import com.aidatachat.adapters.out.mcp.McpAuthProvider;
import com.aidatachat.adapters.out.mcp.McpDestinationPolicy;
import com.aidatachat.adapters.out.mcp.McpHttpClient;
import com.aidatachat.adapters.out.mcp.McpSessionManager;
import com.aidatachat.adapters.out.mcp.NoOpMcpAuthProvider;
import com.aidatachat.adapters.out.mcp.RealMcpGateway;
import com.aidatachat.adapters.out.mime.TikaDocumentMimeDetectionAdapter;
import com.aidatachat.adapters.out.persistence.rag.DocumentJpaAdapter;
import com.aidatachat.adapters.out.persistence.rag.PgVectorSearchAdapter;
import com.aidatachat.adapters.out.persistence.rag.SpringDataDocumentRepository;
import com.aidatachat.adapters.out.provider.AnthropicProviderAdapter;
import com.aidatachat.adapters.out.provider.BytePlusProviderAdapter;
import com.aidatachat.adapters.out.provider.ConfiguredLlmChatGateway;
import com.aidatachat.adapters.out.provider.MiniMaxProviderAdapter;
import com.aidatachat.adapters.out.provider.OllamaProviderAdapter;
import com.aidatachat.adapters.out.provider.OpenAiCompatibleProviderAdapter;
import com.aidatachat.adapters.out.provider.OpenAiProviderAdapter;
import com.aidatachat.adapters.out.provider.ProviderDestinationPolicy;
import com.aidatachat.adapters.out.provider.ProviderHttpClient;
import com.aidatachat.adapters.out.security.AesGcmCredentialCipherAdapter;
import com.aidatachat.adapters.out.security.Argon2PasswordHashAdapter;
import com.aidatachat.adapters.out.security.SpringSessionInvalidationAdapter;
import com.aidatachat.adapters.out.storage.FilesystemDocumentStorageAdapter;
import com.aidatachat.application.port.in.ChatUseCase;
import com.aidatachat.application.port.in.DocumentManagementUseCase;
import com.aidatachat.application.port.in.DocumentProcessingUseCase;
import com.aidatachat.application.port.in.McpStatusUseCase;
import com.aidatachat.application.port.in.RagRetrievalUseCase;
import com.aidatachat.application.port.in.SystemStatusUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.application.port.out.CredentialCipherPort;
import com.aidatachat.application.port.out.DocumentMimeDetectionPort;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.DocumentStoragePort;
import com.aidatachat.application.port.out.DocumentTextExtractionPort;
import com.aidatachat.application.port.out.EmbeddingProviderPort;
import com.aidatachat.application.port.out.IdentityTransactionPort;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.application.port.out.PasswordHashPort;
import com.aidatachat.application.port.out.ProviderConnectionRepository;
import com.aidatachat.application.port.out.SessionInvalidationPort;
import com.aidatachat.application.port.out.UserAccountRepository;
import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.application.service.ChatService;
import com.aidatachat.application.service.DocumentManagementService;
import com.aidatachat.application.service.DocumentProcessingService;
import com.aidatachat.application.service.IdentityService;
import com.aidatachat.application.service.McpStatusService;
import com.aidatachat.application.service.ProviderManagementService;
import com.aidatachat.application.service.RagRetrievalService;
import com.aidatachat.application.service.SystemStatusService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Configuration(proxyBeanMethods = false)
public class ApplicationBeansConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "app.integrations.mode",
            havingValue = "fake",
            matchIfMissing = true)
    FakeLlmProviderAdapter fakeLlmProviderAdapter() {
        return new FakeLlmProviderAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.mcp.mode", havingValue = "fake", matchIfMissing = true)
    FakeMcpGateway fakeMcpGateway() {
        return new FakeMcpGateway();
    }

    /**
     * Always active, regardless of {@code app.integrations.mode}: no real {@link
     * com.aidatachat.application.port.out.EmbeddingProviderPort} adapter exists yet (not approved
     * for this sprint), so there is nothing to toggle to under {@code mode=real} — same rationale
     * as {@link #tikaDocumentMimeDetectionAdapter()}/{@link #documentTextExtractorAdapter}, except
     * this one is a deliberate placeholder rather than an inherently-local implementation. See
     * docs/rag.md for the temporary nature of this wiring.
     */
    @Bean
    FakeEmbeddingProviderAdapter fakeEmbeddingProviderAdapter() {
        return new FakeEmbeddingProviderAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.integrations.mode",
            havingValue = "fake",
            matchIfMissing = true)
    FakeDocumentRepository fakeDocumentRepository() {
        return new FakeDocumentRepository();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.integrations.mode",
            havingValue = "fake",
            matchIfMissing = true)
    FakeDocumentStorageAdapter fakeDocumentStorageAdapter() {
        return new FakeDocumentStorageAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.integrations.mode",
            havingValue = "fake",
            matchIfMissing = true)
    FakeVectorSearchAdapter fakeVectorSearchAdapter() {
        return new FakeVectorSearchAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.mode", havingValue = "real")
    DocumentJpaAdapter documentJpaAdapter(SpringDataDocumentRepository documents) {
        return new DocumentJpaAdapter(documents);
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.mode", havingValue = "real")
    FilesystemDocumentStorageAdapter filesystemDocumentStorageAdapter(
            @Value("${app.rag.storage.path:/var/lib/ai-data-chat/documents}") String basePath) {
        return new FilesystemDocumentStorageAdapter(basePath);
    }

    @Bean
    @ConditionalOnProperty(name = "app.integrations.mode", havingValue = "real")
    PgVectorSearchAdapter pgVectorSearchAdapter(JdbcTemplate jdbcTemplate) {
        return new PgVectorSearchAdapter(jdbcTemplate);
    }

    @Bean
    TikaDocumentMimeDetectionAdapter tikaDocumentMimeDetectionAdapter() {
        return new TikaDocumentMimeDetectionAdapter();
    }

    @Bean
    DocumentTextExtractorAdapter documentTextExtractorAdapter(
            @Value("${app.rag.extraction.max-pages:200}") int maxPages,
            @Value("${app.rag.extraction.max-characters:2000000}") long maxCharacters) {
        return new DocumentTextExtractorAdapter(maxPages, maxCharacters);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService documentProcessingExecutor() {
        return Executors.newCachedThreadPool(
                Thread.ofPlatform().name("document-processing-", 0).daemon(true).factory());
    }

    @Bean
    DocumentProcessingUseCase documentProcessingUseCase(
            DocumentRepository documents,
            DocumentStoragePort storage,
            DocumentTextExtractionPort extraction,
            EmbeddingProviderPort embeddingProvider,
            VectorSearchPort vectorSearch,
            AuditRepository audit,
            Clock clock,
            ExecutorService documentProcessingExecutor,
            @Value("${app.rag.extraction.timeout-seconds:30}") long extractionTimeoutSeconds,
            @Value("${app.rag.chunking.chunk-size-chars:1800}") int chunkSizeChars,
            @Value("${app.rag.chunking.overlap-chars:200}") int overlapChars,
            @Value("${app.rag.chunking.max-chunks-per-document:500}") int maxChunksPerDocument,
            @Value("${app.rag.embedding.model-id:fake-embedding-v1}") String embeddingModelId,
            @Value("${app.rag.embedding.batch-size:64}") int embeddingBatchSize) {
        return new DocumentProcessingService(
                documents,
                storage,
                extraction,
                embeddingProvider,
                vectorSearch,
                audit,
                clock,
                documentProcessingExecutor,
                extractionTimeoutSeconds,
                chunkSizeChars,
                overlapChars,
                maxChunksPerDocument,
                embeddingModelId,
                embeddingBatchSize);
    }

    @Bean
    RagRetrievalUseCase ragRetrievalUseCase(
            DocumentRepository documents,
            EmbeddingProviderPort embeddingProvider,
            VectorSearchPort vectorSearch,
            @Value("${app.rag.embedding.model-id:fake-embedding-v1}") String embeddingModelId,
            @Value("${app.rag.retrieval.top-k:5}") int topK,
            @Value("${app.rag.retrieval.score-threshold:0.5}") double scoreThreshold) {
        return new RagRetrievalService(
                documents, embeddingProvider, vectorSearch, embeddingModelId, topK, scoreThreshold);
    }

    @Bean
    DocumentManagementUseCase documentManagementUseCase(
            DocumentRepository documents,
            DocumentStoragePort storage,
            DocumentMimeDetectionPort mimeDetection,
            AuditRepository audit,
            Clock clock,
            @Value("${app.rag.upload.max-bytes:26214400}") long maxUploadBytes,
            DocumentProcessingUseCase documentProcessingUseCase,
            ExecutorService documentProcessingExecutor) {
        return new DocumentManagementService(
                documents,
                storage,
                mimeDetection,
                audit,
                clock,
                maxUploadBytes,
                documentProcessingUseCase,
                documentProcessingExecutor);
    }

    @Bean
    @ConditionalOnProperty(name = "app.mcp.mode", havingValue = "real")
    McpHttpClient mcpHttpClient(
            @Value("${app.mcp.http.timeout:10s}") Duration timeout,
            @Value("${app.mcp.http.max-response-bytes:1048576}") int maxResponseBytes) {
        return new McpHttpClient(timeout, maxResponseBytes);
    }

    @Bean
    @ConditionalOnProperty(name = "app.mcp.mode", havingValue = "real")
    McpDestinationPolicy mcpDestinationPolicy() {
        return new McpDestinationPolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "app.mcp.mode", havingValue = "real")
    McpAuthProvider mcpAuthProvider() {
        return new NoOpMcpAuthProvider();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "app.mcp.mode", havingValue = "real")
    ScheduledExecutorService mcpStatusRefreshScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("mcp-status-refresh").daemon(true).factory());
    }

    @Bean
    @ConditionalOnProperty(name = "app.mcp.mode", havingValue = "real")
    RealMcpGateway realMcpGateway(
            McpHttpClient http,
            McpDestinationPolicy destinationPolicy,
            McpAuthProvider auth,
            ScheduledExecutorService mcpStatusRefreshScheduler,
            @Value("${app.mcp.base-url}") String baseUrl,
            @Value("${app.mcp.endpoint}") String endpoint,
            @Value("${app.mcp.protocol-version}") String protocolVersion,
            @Value("${app.mcp.required-contract-major}") int requiredContractMajor,
            @Value("${app.mcp.status-refresh-interval:30s}") Duration statusRefreshInterval,
            @Value("${info.application.version:development}") String appVersion) {
        McpSessionManager session =
                new McpSessionManager(
                        http,
                        destinationPolicy,
                        auth,
                        baseUrl,
                        endpoint,
                        protocolVersion,
                        requiredContractMajor,
                        appVersion);
        mcpStatusRefreshScheduler.scheduleAtFixedRate(
                session::refresh, 0, statusRefreshInterval.toMillis(), TimeUnit.MILLISECONDS);
        return new RealMcpGateway(session);
    }

    @Bean
    McpStatusUseCase mcpStatusUseCase(McpGateway mcpGateway) {
        return new McpStatusService(mcpGateway);
    }

    @Bean
    SystemStatusUseCase systemStatusUseCase(
            List<LlmProviderPort> llmProviders,
            McpGateway mcpGateway,
            @Value("${info.application.version:development}") String version,
            @Value("${app.integrations.mode:fake}") String mode) {
        LlmProviderPort statusProvider =
                llmProviders.stream()
                        .filter(provider -> provider.providerType().name().equalsIgnoreCase(mode))
                        .findFirst()
                        .orElse(llmProviders.getFirst());
        return new SystemStatusService(statusProvider, mcpGateway, version, mode);
    }

    @Bean
    CredentialCipherPort credentialCipherPort(
            @Value("${app.providers.credential-master-key}") String masterKey,
            @Value("${app.providers.credential-key-version:1}") int keyVersion) {
        return new AesGcmCredentialCipherAdapter(masterKey, keyVersion);
    }

    @Bean
    ProviderHttpClient providerHttpClient(
            @Value("${app.providers.http.timeout:10s}") Duration timeout,
            @Value("${app.providers.http.max-response-bytes:1048576}") int maxResponseBytes) {
        return new ProviderHttpClient(timeout, maxResponseBytes);
    }

    @Bean
    ProviderDestinationPolicy providerDestinationPolicy(
            @Value("${app.providers.allowed-hosts:}") String allowedHosts,
            @Value("${app.providers.allowed-http-hosts:}") String allowedHttpHosts) {
        return new ProviderDestinationPolicy(allowedHosts, allowedHttpHosts);
    }

    @Bean
    OpenAiProviderAdapter openAiProviderAdapter(ProviderHttpClient http) {
        return new OpenAiProviderAdapter(http);
    }

    @Bean
    AnthropicProviderAdapter anthropicProviderAdapter(ProviderHttpClient http) {
        return new AnthropicProviderAdapter(http);
    }

    @Bean
    BytePlusProviderAdapter bytePlusProviderAdapter(
            ProviderHttpClient http,
            @Value("${app.providers.byteplus.ap-southeast-1-base-url}") String baseUrl) {
        return new BytePlusProviderAdapter(http, Map.of("ap-southeast-1", baseUrl));
    }

    @Bean
    MiniMaxProviderAdapter miniMaxProviderAdapter(
            ProviderHttpClient http, ProviderDestinationPolicy destinationPolicy) {
        return new MiniMaxProviderAdapter(http, destinationPolicy);
    }

    @Bean
    OpenAiCompatibleProviderAdapter openAiCompatibleProviderAdapter(
            ProviderHttpClient http, ProviderDestinationPolicy destinationPolicy) {
        return new OpenAiCompatibleProviderAdapter(http, destinationPolicy);
    }

    @Bean
    OllamaProviderAdapter ollamaProviderAdapter(
            ProviderHttpClient http, ProviderDestinationPolicy destinationPolicy) {
        return new OllamaProviderAdapter(http, destinationPolicy);
    }

    @Bean
    ProviderManagementService providerManagementService(
            ProviderConnectionRepository connections,
            CredentialCipherPort cipher,
            AuditRepository audit,
            List<LlmProviderPort> adapters,
            Clock clock) {
        return new ProviderManagementService(connections, cipher, audit, adapters, clock);
    }

    @Bean
    LlmChatGateway llmChatGateway(
            ProviderConnectionRepository connections,
            CredentialCipherPort cipher,
            List<LlmProviderPort> adapters) {
        return new ConfiguredLlmChatGateway(connections, cipher, adapters);
    }

    @Bean
    ChatUseCase chatUseCase(
            ConversationRepository conversations,
            DocumentRepository documents,
            VectorSearchPort vectorSearch,
            RagRetrievalUseCase ragRetrievalUseCase,
            LlmChatGateway llm,
            McpGateway mcpGateway,
            AuditRepository audit,
            Clock clock,
            @Value("${app.chat.max-history-messages:200}") int maxHistoryMessages,
            @Value("${app.chat.max-history-characters:200000}") int maxHistoryCharacters,
            @Value("${app.chat.max-response-characters:1000000}") int maxResponseCharacters,
            @Value("${app.chat.max-tool-rounds:6}") int maxToolRounds,
            @Value("${app.chat.max-tool-result-bytes:1048576}") int maxToolResultBytes,
            @Value("${app.rag.retrieval.max-context-characters:20000}")
                    int maxRetrievalContextCharacters,
            @Value("${app.mcp.tool-call-timeout:20s}") Duration toolCallTimeout,
            ExecutorService mcpToolOrchestrationExecutor) {
        return new ChatService(
                conversations,
                documents,
                vectorSearch,
                ragRetrievalUseCase,
                llm,
                mcpGateway,
                audit,
                clock,
                maxHistoryMessages,
                maxHistoryCharacters,
                maxResponseCharacters,
                maxToolRounds,
                maxToolResultBytes,
                maxRetrievalContextCharacters,
                toolCallTimeout,
                mcpToolOrchestrationExecutor);
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService chatHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("chat-heartbeat").daemon(true).factory());
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService mcpToolOrchestrationExecutor() {
        return Executors.newCachedThreadPool(
                Thread.ofPlatform().name("mcp-tool-orchestration-", 0).daemon(true).factory());
    }

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    PasswordHashPort passwordHashPort(PasswordEncoder passwordEncoder) {
        return new Argon2PasswordHashAdapter(passwordEncoder);
    }

    @Bean
    SessionInvalidationPort sessionInvalidationPort(JdbcIndexedSessionRepository sessions) {
        return new SpringSessionInvalidationAdapter(sessions);
    }

    @Bean
    IdentityService identityService(
            UserAccountRepository users,
            PasswordHashPort passwordHash,
            AuditRepository audit,
            IdentityTransactionPort transactions,
            SessionInvalidationPort sessions,
            Clock clock,
            @Value("${app.identity.allow-public-registration:true}")
                    boolean publicRegistrationAllowed) {
        return new IdentityService(
                users,
                passwordHash,
                audit,
                transactions,
                sessions,
                clock,
                publicRegistrationAllowed);
    }
}
