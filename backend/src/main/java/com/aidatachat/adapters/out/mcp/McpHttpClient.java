package com.aidatachat.adapters.out.mcp;

import com.aidatachat.application.exception.McpCommunicationException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Blocking JSON-RPC transport for the MCP Streamable HTTP client, mirroring ProviderHttpClient. */
public final class McpHttpClient {

    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient webClient;
    private final Duration timeout;

    public McpHttpClient(Duration timeout, int maxResponseBytes) {
        this.timeout = timeout;
        HttpClient httpClient = HttpClient.create().followRedirect(false).responseTimeout(timeout);
        this.webClient =
                WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxResponseBytes))
                        .build();
    }

    public JsonResponse post(URI uri, Consumer<HttpHeaders> headers, JsonNode body) {
        try {
            return webClient
                    .post()
                    .uri(uri)
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .exchangeToMono(this::readResponse)
                    .timeout(timeout)
                    .block();
        } catch (McpCommunicationException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw normalizeNetworkFailure(exception);
        } catch (RuntimeException exception) {
            throw normalizeNetworkFailure(exception);
        }
    }

    private Mono<JsonResponse> readResponse(ClientResponse response) {
        String sessionId = response.headers().asHttpHeaders().getFirst(SESSION_HEADER);
        HttpStatusCode status = response.statusCode();
        if (status.value() == 202) {
            return response.releaseBody().then(Mono.just(new JsonResponse(null, sessionId)));
        }
        if (!status.is2xxSuccessful()) {
            return response.releaseBody()
                    .then(
                            Mono.error(
                                    new McpCommunicationException(
                                            errorCode(status.value()),
                                            null,
                                            retryable(status.value()),
                                            null)));
        }
        boolean eventStream =
                response.headers()
                        .contentType()
                        .map(type -> type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .orElse(false);
        if (eventStream) {
            return response.bodyToFlux(String.class)
                    .collectList()
                    .flatMap(
                            frames -> {
                                JsonNode message = selectSseMessage(frames);
                                if (message == null) {
                                    return Mono.error(
                                            new McpCommunicationException(
                                                    "MCP_INVALID_RESPONSE", null, false, null));
                                }
                                return Mono.just(new JsonResponse(message, sessionId));
                            });
        }
        return response.bodyToMono(JsonNode.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("MCP response is empty")))
                .map(body -> new JsonResponse(body, sessionId));
    }

    // A POST may emit several SSE events (progress notifications before the reply); prefer the
    // last one carrying an "id", since that's the actual JSON-RPC response.
    private JsonNode selectSseMessage(List<String> frames) {
        JsonNode fallback = null;
        for (String frame : frames) {
            JsonNode parsed;
            try {
                parsed = JSON.readTree(frame);
            } catch (RuntimeException exception) {
                continue;
            }
            if (parsed.has("id")) {
                fallback = parsed;
            } else if (fallback == null) {
                fallback = parsed;
            }
        }
        return fallback;
    }

    private McpCommunicationException normalizeNetworkFailure(RuntimeException exception) {
        boolean timedOut =
                exception.getClass().getSimpleName().toLowerCase().contains("timeout")
                        || (exception.getCause() != null
                                && exception
                                        .getCause()
                                        .getClass()
                                        .getSimpleName()
                                        .toLowerCase()
                                        .contains("timeout"));
        return new McpCommunicationException(
                timedOut ? "MCP_TIMEOUT" : "MCP_UNAVAILABLE", null, true, null);
    }

    private String errorCode(int status) {
        return switch (status) {
            case 401, 403 -> "MCP_AUTHENTICATION_FAILED";
            case 404 -> "MCP_SESSION_LOST";
            case 408, 504 -> "MCP_TIMEOUT";
            default -> status >= 500 ? "MCP_UNAVAILABLE" : "MCP_INVALID_RESPONSE";
        };
    }

    private boolean retryable(int status) {
        return status == 404 || status == 408 || status >= 500;
    }

    public record JsonResponse(JsonNode body, String sessionId) {}
}
