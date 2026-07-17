package com.aidatachat.adapters.out.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidatachat.application.port.out.LlmProviderPort.ProviderClientConfiguration;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
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
    void miniMaxUsesFixedBaseUrlAndBoundedProbe() {
        server.createContext(
                "/minimax/chat/completions",
                exchange -> {
                    String body =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    boolean valid =
                            "Bearer minimax-test-key"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("Authorization"))
                                    && body.contains("\"max_tokens\":1")
                                    && body.contains("\"model\":\"MiniMax-M2\"");
                    send(exchange, valid ? 200 : 400, valid ? "{\"choices\":[]}" : "{}");
                });
        MiniMaxProviderAdapter adapter = new MiniMaxProviderAdapter(http, baseUrl + "/minimax");
        ProviderClientConfiguration configuration =
                new ProviderClientConfiguration(null, null, null, null, null, "MiniMax-M2");

        assertThat(
                        adapter.testConnection(configuration, "minimax-test-key".toCharArray())
                                .success())
                .isTrue();
        assertThat(adapter.discoverModels(configuration, "minimax-test-key".toCharArray()))
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

    @Test
    void openAiAndAnthropicNormalizeOfficialSseEvents() throws Exception {
        server.createContext(
                "/openai/responses",
                exchange ->
                        sendStream(
                                exchange,
                                "text/event-stream",
                                """
                                data: {"type":"response.output_text.delta","delta":"Hola "}

                                data: {"type":"response.completed","response":{"id":"resp-test","status":"completed","usage":{"input_tokens":3,"output_tokens":2}}}

                                """));
        server.createContext(
                "/anthropic/messages",
                exchange ->
                        sendStream(
                                exchange,
                                "text/event-stream",
                                """
                                event: message_start
                                data: {"type":"message_start","message":{"id":"msg-test","usage":{"input_tokens":4}}}

                                event: content_block_delta
                                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Claude"}}

                                event: message_delta
                                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}

                                event: message_stop
                                data: {"type":"message_stop"}

                                """));
        LlmChatRequest request = request("model-test");
        ChunkSubscriber openAi = new ChunkSubscriber();
        ChunkSubscriber anthropic = new ChunkSubscriber();

        new OpenAiProviderAdapter(http, baseUrl + "/openai")
                .streamChat(config(), "openai-key".toCharArray(), request)
                .subscribe(openAi);
        new AnthropicProviderAdapter(http, baseUrl + "/anthropic")
                .streamChat(config(), "anthropic-key".toCharArray(), request)
                .subscribe(anthropic);

        assertThat(openAi.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(anthropic.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(openAi.failure).isNull();
        assertThat(openAi.chunks).extracting(LlmChunk::content).containsExactly("Hola ", "");
        assertThat(openAi.chunks.getLast().inputTokens()).isEqualTo(3);
        assertThat(openAi.chunks.getLast().outputTokens()).isEqualTo(2);
        assertThat(anthropic.failure).isNull();
        assertThat(anthropic.chunks).extracting(LlmChunk::content).contains("Claude");
        assertThat(anthropic.chunks.getLast().finished()).isTrue();
    }

    @Test
    void openAiAndAnthropicNormalizeToolCallStreamingEvents() throws Exception {
        server.createContext(
                "/openai-tools/responses",
                exchange ->
                        sendStream(
                                exchange,
                                "text/event-stream",
                                """
                                data: {"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","call_id":"call_1","name":"health_check"}}

                                data: {"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\\"a\\":1}"}

                                data: {"type":"response.completed","response":{"id":"resp-tools","status":"completed","usage":{"input_tokens":3,"output_tokens":2},"output":[{"type":"function_call"}]}}

                                """));
        server.createContext(
                "/anthropic-tools/messages",
                exchange ->
                        sendStream(
                                exchange,
                                "text/event-stream",
                                """
                                event: content_block_start
                                data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"health_check"}}

                                event: content_block_delta
                                data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"a\\":1}"}}

                                event: message_delta
                                data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":2}}

                                """));
        LlmChatRequest request = request("model-test");
        ChunkSubscriber openAi = new ChunkSubscriber();
        ChunkSubscriber anthropic = new ChunkSubscriber();

        new OpenAiProviderAdapter(http, baseUrl + "/openai-tools")
                .streamChat(config(), "openai-key".toCharArray(), request)
                .subscribe(openAi);
        new AnthropicProviderAdapter(http, baseUrl + "/anthropic-tools")
                .streamChat(config(), "anthropic-key".toCharArray(), request)
                .subscribe(anthropic);

        assertThat(openAi.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(anthropic.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(openAi.failure).isNull();
        assertThat(openAi.chunks)
                .flatExtracting(LlmChunk::toolCalls)
                .extracting(delta -> delta.toolName())
                .contains("health_check");
        assertThat(openAi.chunks.getLast().finishReason()).isEqualTo("tool_calls");
        assertThat(anthropic.failure).isNull();
        assertThat(anthropic.chunks)
                .flatExtracting(LlmChunk::toolCalls)
                .extracting(delta -> delta.toolName())
                .contains("health_check");
        assertThat(anthropic.chunks.getLast().finishReason()).isEqualTo("tool_use");
    }

    @Test
    void bytePlusCompatibleAndOllamaNormalizeTheirStreamingFormats() throws Exception {
        String chatCompletionStream =
                """
                data: {"id":"chat-test","choices":[{"delta":{"content":"Dato"},"finish_reason":null}]}

                data: {"id":"chat-test","choices":[{"delta":{},"finish_reason":"stop"}]}

                """;
        server.createContext(
                "/byteplus/chat/completions",
                exchange -> sendStream(exchange, "text/event-stream", chatCompletionStream));
        server.createContext(
                "/minimax/chat/completions",
                exchange -> sendStream(exchange, "text/event-stream", chatCompletionStream));
        server.createContext(
                "/compatible/chat/completions",
                exchange -> sendStream(exchange, "text/event-stream", chatCompletionStream));
        server.createContext(
                "/ollama/api/chat",
                exchange ->
                        sendStream(
                                exchange,
                                "application/x-ndjson",
                                """
                                {"message":{"role":"assistant","content":"Local"},"done":false}
                                {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":5,"eval_count":1}
                                """));
        LlmChatRequest request = request("model-test");
        ProviderDestinationPolicy policy = new ProviderDestinationPolicy("127.0.0.1", "127.0.0.1");
        ChunkSubscriber bytePlus = new ChunkSubscriber();
        ChunkSubscriber miniMax = new ChunkSubscriber();
        ChunkSubscriber compatible = new ChunkSubscriber();
        ChunkSubscriber ollama = new ChunkSubscriber();

        new BytePlusProviderAdapter(http, Map.of("test", baseUrl + "/byteplus"))
                .streamChat(
                        new ProviderClientConfiguration(
                                null, "test", null, null, null, "model-test"),
                        "key".toCharArray(),
                        request)
                .subscribe(bytePlus);
        new MiniMaxProviderAdapter(http, baseUrl + "/minimax")
                .streamChat(
                        new ProviderClientConfiguration(null, null, null, null, null, "model-test"),
                        "key".toCharArray(),
                        request)
                .subscribe(miniMax);
        new OpenAiCompatibleProviderAdapter(http, policy)
                .streamChat(
                        new ProviderClientConfiguration(
                                baseUrl + "/compatible",
                                null,
                                null,
                                null,
                                "/chat/completions",
                                "model-test"),
                        "key".toCharArray(),
                        request)
                .subscribe(compatible);
        new OllamaProviderAdapter(http, policy)
                .streamChat(
                        new ProviderClientConfiguration(
                                baseUrl + "/ollama", null, null, null, null, null),
                        new char[0],
                        request)
                .subscribe(ollama);

        assertThat(bytePlus.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(miniMax.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(compatible.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(ollama.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(bytePlus.content()).isEqualTo("Dato");
        assertThat(miniMax.content()).isEqualTo("Dato");
        assertThat(compatible.content()).isEqualTo("Dato");
        assertThat(ollama.content()).isEqualTo("Local");
        assertThat(ollama.chunks.getLast().inputTokens()).isEqualTo(5);
        assertThat(ollama.chunks.getLast().outputTokens()).isEqualTo(1);
    }

    private ProviderClientConfiguration config() {
        return ProviderClientConfiguration.empty();
    }

    private LlmChatRequest request(String modelId) {
        return new LlmChatRequest(modelId, List.of(new ChatMessage("user", "hola")));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void sendStream(HttpExchange exchange, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("x-request-id", "provider-request-test");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class ChunkSubscriber implements Flow.Subscriber<LlmChunk> {

        private final List<LlmChunk> chunks = new ArrayList<>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(LlmChunk item) {
            chunks.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            finished.countDown();
        }

        @Override
        public void onComplete() {
            finished.countDown();
        }

        private String content() {
            return chunks.stream().map(LlmChunk::content).reduce("", String::concat);
        }
    }
}
