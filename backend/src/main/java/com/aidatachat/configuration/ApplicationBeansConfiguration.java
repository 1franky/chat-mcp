package com.aidatachat.configuration;

import com.aidatachat.adapters.out.fake.FakeLlmProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeMcpGateway;
import com.aidatachat.adapters.out.security.Argon2PasswordHashAdapter;
import com.aidatachat.adapters.out.security.SpringSessionInvalidationAdapter;
import com.aidatachat.application.port.in.SystemStatusUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.IdentityTransactionPort;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.application.port.out.PasswordHashPort;
import com.aidatachat.application.port.out.SessionInvalidationPort;
import com.aidatachat.application.port.out.UserAccountRepository;
import com.aidatachat.application.service.IdentityService;
import com.aidatachat.application.service.SystemStatusService;
import java.time.Clock;
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
            LlmProviderPort llmProvider,
            McpGateway mcpGateway,
            @Value("${info.application.version:development}") String version,
            @Value("${app.integrations.mode:fake}") String mode) {
        return new SystemStatusService(llmProvider, mcpGateway, version, mode);
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
