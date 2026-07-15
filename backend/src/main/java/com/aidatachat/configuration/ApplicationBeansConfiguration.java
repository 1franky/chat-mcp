package com.aidatachat.configuration;

import com.aidatachat.adapters.out.fake.FakeLlmProviderAdapter;
import com.aidatachat.adapters.out.fake.FakeMcpGateway;
import com.aidatachat.application.port.in.SystemStatusUseCase;
import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.application.service.SystemStatusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
