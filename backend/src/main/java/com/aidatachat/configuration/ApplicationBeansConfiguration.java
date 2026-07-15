package com.aidatachat.configuration;

import com.aidatachat.adapters.out.fake.FakeLlmProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeMcpGateway;
import com.aidatachat.adapters.out.provider.AnthropicProviderAdapter;
import com.aidatachat.adapters.out.provider.BytePlusProviderAdapter;
import com.aidatachat.adapters.out.provider.ConfiguredLlmChatGateway;
import com.aidatachat.adapters.out.provider.OllamaProviderAdapter;
import com.aidatachat.adapters.out.provider.OpenAiCompatibleProviderAdapter;
import com.aidatachat.adapters.out.provider.OpenAiProviderAdapter;
import com.aidatachat.adapters.out.provider.ProviderDestinationPolicy;
import com.aidatachat.adapters.out.provider.ProviderHttpClient;
import com.aidatachat.adapters.out.security.AesGcmCredentialCipherAdapter;
import com.aidatachat.adapters.out.security.Argon2PasswordHashAdapter;
import com.aidatachat.adapters.out.security.SpringSessionInvalidationAdapter;
import com.aidatachat.application.port.in.ChatUseCase;
import com.aidatachat.application.port.in.SystemStatusUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.application.port.out.CredentialCipherPort;
import com.aidatachat.application.port.out.IdentityTransactionPort;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.application.port.out.PasswordHashPort;
import com.aidatachat.application.port.out.ProviderConnectionRepository;
import com.aidatachat.application.port.out.SessionInvalidationPort;
import com.aidatachat.application.port.out.UserAccountRepository;
import com.aidatachat.application.service.ChatService;
import com.aidatachat.application.service.IdentityService;
import com.aidatachat.application.service.ProviderManagementService;
import com.aidatachat.application.service.SystemStatusService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    @ConditionalOnProperty(
            name = "app.integrations.mode",
            havingValue = "fake",
            matchIfMissing = true)
    FakeMcpGateway fakeMcpGateway() {
        return new FakeMcpGateway();
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
            LlmChatGateway llm,
            AuditRepository audit,
            Clock clock,
            @Value("${app.chat.max-history-messages:200}") int maxHistoryMessages,
            @Value("${app.chat.max-history-characters:200000}") int maxHistoryCharacters,
            @Value("${app.chat.max-response-characters:1000000}") int maxResponseCharacters) {
        return new ChatService(
                conversations,
                llm,
                audit,
                clock,
                maxHistoryMessages,
                maxHistoryCharacters,
                maxResponseCharacters);
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService chatHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("chat-heartbeat").daemon(true).factory());
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
