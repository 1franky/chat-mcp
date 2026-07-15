package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

final class ProviderStreamingSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProviderStreamingSupport() {}

    static Flow.Publisher<LlmChunk> map(
            Flow.Publisher<ProviderHttpClient.StreamFrame> source,
            Function<ProviderHttpClient.StreamFrame, LlmChunk> mapper) {
        return subscriber ->
                source.subscribe(
                        new Flow.Subscriber<>() {
                            private Flow.Subscription upstream;

                            @Override
                            public void onSubscribe(Flow.Subscription subscription) {
                                upstream = subscription;
                                subscriber.onSubscribe(
                                        new Flow.Subscription() {
                                            @Override
                                            public void request(long count) {
                                                subscription.request(count);
                                            }

                                            @Override
                                            public void cancel() {
                                                subscription.cancel();
                                            }
                                        });
                            }

                            @Override
                            public void onNext(ProviderHttpClient.StreamFrame frame) {
                                try {
                                    LlmChunk mapped = mapper.apply(frame);
                                    if (mapped == null) {
                                        upstream.request(1);
                                    } else {
                                        subscriber.onNext(mapped);
                                    }
                                } catch (RuntimeException exception) {
                                    upstream.cancel();
                                    subscriber.onError(exception);
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                subscriber.onError(throwable);
                            }

                            @Override
                            public void onComplete() {
                                subscriber.onComplete();
                            }
                        });
    }

    static ObjectNode responsesBody(LlmChatRequest request) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", request.modelId());
        body.put("stream", true);
        body.put("store", false);
        ArrayNode input = body.putArray("input");
        addMessages(input, request);
        return body;
    }

    static ObjectNode chatCompletionsBody(LlmChatRequest request, int maxTokens) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", request.modelId());
        body.put("stream", true);
        if (maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        ArrayNode messages = body.putArray("messages");
        addMessages(messages, request);
        return body;
    }

    static ObjectNode anthropicBody(LlmChatRequest request, int maxTokens) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", request.modelId());
        body.put("stream", true);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        addMessages(messages, request);
        return body;
    }

    static ObjectNode ollamaBody(LlmChatRequest request) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", request.modelId());
        body.put("stream", true);
        ArrayNode messages = body.putArray("messages");
        addMessages(messages, request);
        return body;
    }

    static LlmChunk parseOpenAiResponses(ProviderHttpClient.StreamFrame frame) {
        JsonNode event = parse(frame);
        String type = event.path("type").asText("");
        return switch (type) {
            case "response.output_text.delta" ->
                    LlmChunk.delta(event.path("delta").asText(""), frame.requestId());
            case "response.completed" -> {
                JsonNode response = event.path("response");
                JsonNode usage = response.path("usage");
                yield LlmChunk.completed(
                        integer(usage, "input_tokens"),
                        integer(usage, "output_tokens"),
                        response.path("status").asText("completed"),
                        requestId(frame, response.path("id").asText(null)));
            }
            case "response.incomplete" -> {
                JsonNode response = event.path("response");
                yield LlmChunk.completed(
                        integer(response.path("usage"), "input_tokens"),
                        integer(response.path("usage"), "output_tokens"),
                        response.path("incomplete_details").path("reason").asText("incomplete"),
                        requestId(frame, response.path("id").asText(null)));
            }
            case "response.failed", "error" -> throw streamFailure(frame, event);
            default -> null;
        };
    }

    static LlmChunk parseChatCompletions(ProviderHttpClient.StreamFrame frame) {
        if ("[DONE]".equals(frame.data().strip())) {
            return LlmChunk.completed(null, null, "stop", frame.requestId());
        }
        JsonNode event = parse(frame);
        JsonNode choices = event.path("choices");
        JsonNode usage = event.path("usage");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            String content = choice.path("delta").path("content").asText("");
            String finishReason = choice.path("finish_reason").asText(null);
            if (!content.isEmpty()) {
                return new LlmChunk(
                        content,
                        false,
                        integer(usage, "prompt_tokens"),
                        integer(usage, "completion_tokens"),
                        finishReason,
                        requestId(frame, event.path("id").asText(null)));
            }
            if (finishReason != null) {
                return LlmChunk.completed(
                        integer(usage, "prompt_tokens"),
                        integer(usage, "completion_tokens"),
                        finishReason,
                        requestId(frame, event.path("id").asText(null)));
            }
        }
        if (!usage.isMissingNode() && !usage.isNull()) {
            return new LlmChunk(
                    "",
                    false,
                    integer(usage, "prompt_tokens"),
                    integer(usage, "completion_tokens"),
                    null,
                    requestId(frame, event.path("id").asText(null)));
        }
        if (event.has("error")) {
            throw streamFailure(frame, event);
        }
        return null;
    }

    static LlmChunk parseAnthropic(ProviderHttpClient.StreamFrame frame) {
        JsonNode event = parse(frame);
        String type = event.path("type").asText("");
        return switch (type) {
            case "message_start" -> {
                JsonNode message = event.path("message");
                yield new LlmChunk(
                        "",
                        false,
                        integer(message.path("usage"), "input_tokens"),
                        null,
                        null,
                        requestId(frame, message.path("id").asText(null)));
            }
            case "content_block_delta" -> {
                JsonNode delta = event.path("delta");
                yield "text_delta".equals(delta.path("type").asText())
                        ? LlmChunk.delta(delta.path("text").asText(""), frame.requestId())
                        : null;
            }
            case "message_delta" ->
                    new LlmChunk(
                            "",
                            false,
                            null,
                            integer(event.path("usage"), "output_tokens"),
                            event.path("delta").path("stop_reason").asText(null),
                            frame.requestId());
            case "message_stop" -> LlmChunk.completed(null, null, "stop", frame.requestId());
            case "error" -> throw streamFailure(frame, event);
            default -> null;
        };
    }

    static LlmChunk parseOllama(ProviderHttpClient.StreamFrame frame) {
        JsonNode event = parse(frame);
        if (event.has("error")) {
            throw streamFailure(frame, event);
        }
        String content = event.path("message").path("content").asText("");
        boolean done = event.path("done").asBoolean(false);
        return new LlmChunk(
                content,
                done,
                integer(event, "prompt_eval_count"),
                integer(event, "eval_count"),
                done ? event.path("done_reason").asText("stop") : null,
                frame.requestId());
    }

    private static void addMessages(ArrayNode target, LlmChatRequest request) {
        for (ChatMessage message : request.messages()) {
            target.addObject().put("role", message.role()).put("content", message.content());
        }
    }

    private static JsonNode parse(ProviderHttpClient.StreamFrame frame) {
        try {
            return JSON.readTree(frame.data());
        } catch (JacksonException exception) {
            throw new ProviderCommunicationException(
                    "PROVIDER_INVALID_STREAM", frame.requestId(), false, null);
        }
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() && value.canConvertToInt() ? value.asInt() : null;
    }

    private static String requestId(ProviderHttpClient.StreamFrame frame, String bodyId) {
        return frame.requestId() == null ? bodyId : frame.requestId();
    }

    private static ProviderCommunicationException streamFailure(
            ProviderHttpClient.StreamFrame frame, JsonNode event) {
        Objects.requireNonNull(event);
        String type = event.path("error").path("type").asText("");
        String code =
                "overloaded_error".equals(type)
                        ? "PROVIDER_RATE_LIMITED"
                        : "PROVIDER_STREAM_FAILED";
        return new ProviderCommunicationException(
                code, frame.requestId(), "PROVIDER_RATE_LIMITED".equals(code), null);
    }
}
