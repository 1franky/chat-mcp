package com.aidatachat.adapters.out.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderAdaptersTest {

    private HttpServer server;
    private String baseUrl;
    private ProviderHttpClient http;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new ProviderHttpClient(Duration.ofSeconds(2), 64 * 1024);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void openAiUsesBearerAuthenticationAndDiscoversModels() {
        server.createContext(
                "/openai/models",
                exchange -> {
                    if (!"Bearer openai-test-key"
                            .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        send(exchange, 401, "{}");
                        return;
                    }
                    exchange.getResponseHeaders().add("x-request-id", "req_openai");
                    send(exchange, 200, "{\"data\":[{\"id\":\"model-a\"}]}");
                });
        OpenAiProviderAdapter adapter = new OpenAiProviderAdapter(http, baseUrl + "/openai");

        assertThat(adapter.testConnection(config(), "wrong".toCharArray()).success()).isFalse();
        assertThat(adapter.testConnection(config(), "wrong".toCharArray()).code())
                .isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
        assertThat(adapter.testConnection(config(), "openai-test-key".toCharArray()).success())
                .isTrue();
        assertThat(adapter.discoverModels(config(), "openai-test-key".toCharArray()))
                .extracting(model -> model.modelId())
                .containsExactly("model-a");
    }

    @Test
    void anthropicSendsVersionedHeadersAndReadsDisplayName() {
        server.createContext(
                "/anthropic/models",
                exchange -> {
                    boolean valid =
                            "anthropic-test-key"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("x-api-key"))
                                    && "2023-06-01"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("anthropic-version"));
                    send(
                            exchange,
                            valid ? 200 : 401,
                            valid
                                    ? "{\"data\":[{\"id\":\"claude-test\",\"display_name\":\"Claude Test\"}]}"
                                    : "{}");
                });
        AnthropicProviderAdapter adapter =
                new AnthropicProviderAdapter(http, baseUrl + "/anthropic");

        assertThat(adapter.discoverModels(config(), "anthropic-test-key".toCharArray()))
                .singleElement()
                .satisfies(
                        model -> {
                            assertThat(model.modelId()).isEqualTo("claude-test");
                            assertThat(model.displayName()).isEqualTo("Claude Test");
                        });
    }

    @Test
    void bytePlusUsesConfiguredRegionAndBoundedProbe() {
        server.createContext(
                "/byteplus/chat/completions",
                exchange -> {
                    String body =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    boolean valid =
                            "Bearer byteplus-test-key"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("Authorization"))
                                    && body.contains("\"max_tokens\":1")
                                    && body.contains("\"model\":\"endpoint-test\"");
                    send(exchange, valid ? 200 : 400, valid ? "{\"choices\":[]}" : "{}");
                });
        BytePlusProviderAdapter adapter =
                new BytePlusProviderAdapter(http, Map.of("test-region", baseUrl + "/byteplus"));
        ProviderClientConfiguration configuration =
                new ProviderClientConfiguration(
                        null, "test-region", null, null, null, "endpoint-test");

        assertThat(
                        adapter.testConnection(configuration, "byteplus-test-key".toCharArray())
                                .success())
                .isTrue();
        assertThat(adapter.discoverModels(configuration, "byteplus-test-key".toCharArray()))
                .isEmpty();
    }

    @Test
    void compatibleAndOllamaAdaptersUseOnlyAllowlistedDestinations() {
        server.createContext(
                "/compatible/models",
                exchange -> send(exchange, 200, "{\"data\":[{\"id\":\"compatible-a\"}]}"));
        server.createContext(
                "/ollama/api/tags",
                exchange -> send(exchange, 200, "{\"models\":[{\"model\":\"llama-test\"}]}"));
        ProviderDestinationPolicy policy = new ProviderDestinationPolicy("127.0.0.1", "127.0.0.1");
        OpenAiCompatibleProviderAdapter compatible =
                new OpenAiCompatibleProviderAdapter(http, policy);
        OllamaProviderAdapter ollama = new OllamaProviderAdapter(http, policy);

        ProviderClientConfiguration compatibleConfiguration =
                new ProviderClientConfiguration(
                        baseUrl + "/compatible", null, "/models", null, null, null);
        ProviderClientConfiguration ollamaConfiguration =
                new ProviderClientConfiguration(baseUrl + "/ollama", null, null, null, null, null);

        assertThat(compatible.discoverModels(compatibleConfiguration, "key".toCharArray()))
                .extracting(model -> model.modelId())
                .containsExactly("compatible-a");
        assertThat(ollama.discoverModels(ollamaConfiguration, new char[0]))
                .extracting(model -> model.modelId())
                .containsExactly("llama-test");
    }

    private ProviderClientConfiguration config() {
        return ProviderClientConfiguration.empty();
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
