package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
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

public final class ProviderHttpClient {

    private static final List<String> REQUEST_ID_HEADERS =
            List.of("x-request-id", "request-id", "x-client-request-id");

    private final WebClient webClient;
    private final Duration timeout;

    public ProviderHttpClient(Duration timeout, int maxResponseBytes) {
        this.timeout = timeout;
        HttpClient httpClient = HttpClient.create().followRedirect(false).responseTimeout(timeout);
        this.webClient =
                WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxResponseBytes))
                        .build();
    }

    public JsonResponse get(URI uri, Consumer<HttpHeaders> headers) {
        try {
            return webClient
                    .get()
                    .uri(uri)
                    .headers(headers)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(this::readResponse)
                    .timeout(timeout)
                    .block();
        } catch (ProviderCommunicationException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw normalizeNetworkFailure(exception);
        } catch (RuntimeException exception) {
            throw normalizeNetworkFailure(exception);
        }
    }

    public JsonResponse post(URI uri, Consumer<HttpHeaders> headers, JsonNode body) {
        try {
            return webClient
                    .post()
                    .uri(uri)
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(this::readResponse)
                    .timeout(timeout)
                    .block();
        } catch (ProviderCommunicationException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw normalizeNetworkFailure(exception);
        } catch (RuntimeException exception) {
            throw normalizeNetworkFailure(exception);
        }
    }

    private Mono<JsonResponse> readResponse(ClientResponse response) {
        String requestId = requestId(response.headers().asHttpHeaders());
        HttpStatusCode status = response.statusCode();
        if (!status.is2xxSuccessful()) {
            return response.releaseBody()
                    .then(
                            Mono.error(
                                    new ProviderCommunicationException(
                                            errorCode(status.value()),
                                            requestId,
                                            retryable(status.value()),
                                            null)));
        }
        return response.bodyToMono(JsonNode.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Provider response is empty")))
                .map(body -> new JsonResponse(body, requestId));
    }

    private ProviderCommunicationException normalizeNetworkFailure(RuntimeException exception) {
        String code =
                exception.getClass().getSimpleName().toLowerCase().contains("timeout")
                                || (exception.getCause() != null
                                        && exception
                                                .getCause()
                                                .getClass()
                                                .getSimpleName()
                                                .toLowerCase()
                                                .contains("timeout"))
                        ? "PROVIDER_TIMEOUT"
                        : "PROVIDER_UNAVAILABLE";
        return new ProviderCommunicationException(code, null, true, null);
    }

    private String errorCode(int status) {
        return switch (status) {
            case 401, 403 -> "PROVIDER_AUTHENTICATION_FAILED";
            case 404 -> "PROVIDER_RESOURCE_NOT_FOUND";
            case 408, 504 -> "PROVIDER_TIMEOUT";
            case 429 -> "PROVIDER_RATE_LIMITED";
            default ->
                    status >= 300 && status < 400
                            ? "PROVIDER_REDIRECT_BLOCKED"
                            : status >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_REQUEST_REJECTED";
        };
    }

    private boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private String requestId(HttpHeaders headers) {
        return REQUEST_ID_HEADERS.stream()
                .map(headers::getFirst)
                .filter(value -> value != null && value.length() <= 200)
                .findFirst()
                .orElse(null);
    }

    public record JsonResponse(JsonNode body, String requestId) {}
}
