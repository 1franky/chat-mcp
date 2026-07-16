package com.aidatachat.adapters.out.provider;

import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.LlmToolCall;
import com.aidatachat.domain.model.LlmToolCallDelta;
import com.aidatachat.domain.model.McpToolDefinition;
import java.util.List;
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
        if (!request.tools().isEmpty()) {
            body.set("tools", toolsForResponses(request.tools()));
        }
        ArrayNode input = body.putArray("input");
        addResponsesMessages(input, request);
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
        if (!request.tools().isEmpty()) {
            body.set("tools", toolsForAnthropic(request.tools()));
        }
        ArrayNode messages = body.putArray("messages");
        addAnthropicMessages(messages, request);
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
            case "response.output_item.added" -> {
                JsonNode item = event.path("item");
                yield "function_call".equals(item.path("type").asText(""))
                        ? LlmChunk.toolCallDelta(
                                List.of(
                                        new LlmToolCallDelta(
                                                event.path("output_index").asInt(0),
                                                item.path("call_id").asText(null),
                                                item.path("name").asText(null),
                                                "")),
                                frame.requestId())
                        : null;
            }
            case "response.function_call_arguments.delta" ->
                    LlmChunk.toolCallDelta(
                            List.of(
                                    new LlmToolCallDelta(
                                            event.path("output_index").asInt(0),
                                            null,
                                            null,
                                            event.path("delta").asText(""))),
                            frame.requestId());
            case "response.completed" -> {
                JsonNode response = event.path("response");
                JsonNode usage = response.path("usage");
                String finishReason =
                        hasFunctionCall(response.path("output"))
                                ? "tool_calls"
                                : response.path("status").asText("completed");
                yield LlmChunk.completed(
                        integer(usage, "input_tokens"),
                        integer(usage, "output_tokens"),
                        finishReason,
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
            case "content_block_start" -> {
                JsonNode contentBlock = event.path("content_block");
                yield "tool_use".equals(contentBlock.path("type").asText(""))
                        ? LlmChunk.toolCallDelta(
                                List.of(
                                        new LlmToolCallDelta(
                                                event.path("index").asInt(0),
                                                contentBlock.path("id").asText(null),
                                                contentBlock.path("name").asText(null),
                                                "")),
                                frame.requestId())
                        : null;
            }
            case "content_block_delta" -> {
                JsonNode delta = event.path("delta");
                yield switch (delta.path("type").asText("")) {
                    case "text_delta" ->
                            LlmChunk.delta(delta.path("text").asText(""), frame.requestId());
                    case "input_json_delta" ->
                            LlmChunk.toolCallDelta(
                                    List.of(
                                            new LlmToolCallDelta(
                                                    event.path("index").asInt(0),
                                                    null,
                                                    null,
                                                    delta.path("partial_json").asText(""))),
                                    frame.requestId());
                    default -> null;
                };
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

    private static void addResponsesMessages(ArrayNode target, LlmChatRequest request) {
        for (ChatMessage message : request.messages()) {
            if ("tool".equals(message.role())) {
                ObjectNode output = target.addObject();
                output.put("type", "function_call_output");
                output.put("call_id", message.toolCallId());
                output.put("output", message.content());
                continue;
            }
            if (!message.toolCalls().isEmpty()) {
                if (!message.content().isEmpty()) {
                    target.addObject()
                            .put("role", message.role())
                            .put("content", message.content());
                }
                for (LlmToolCall toolCall : message.toolCalls()) {
                    ObjectNode call = target.addObject();
                    call.put("type", "function_call");
                    call.put("call_id", toolCall.id());
                    call.put("name", toolCall.name());
                    call.put("arguments", JSON.valueToTree(toolCall.arguments()).toString());
                }
                continue;
            }
            target.addObject().put("role", message.role()).put("content", message.content());
        }
    }

    private static void addAnthropicMessages(ArrayNode target, LlmChatRequest request) {
        for (ChatMessage message : request.messages()) {
            if ("tool".equals(message.role())) {
                ObjectNode toolResultMessage = target.addObject();
                toolResultMessage.put("role", "user");
                ArrayNode content = toolResultMessage.putArray("content");
                ObjectNode toolResult = content.addObject();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", message.toolCallId());
                toolResult.put("content", message.content());
                continue;
            }
            if (!message.toolCalls().isEmpty()) {
                ObjectNode assistantMessage = target.addObject();
                assistantMessage.put("role", "assistant");
                ArrayNode content = assistantMessage.putArray("content");
                if (!message.content().isEmpty()) {
                    content.addObject().put("type", "text").put("text", message.content());
                }
                for (LlmToolCall toolCall : message.toolCalls()) {
                    ObjectNode toolUse = content.addObject();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", toolCall.id());
                    toolUse.put("name", toolCall.name());
                    toolUse.set("input", JSON.valueToTree(toolCall.arguments()));
                }
                continue;
            }
            target.addObject().put("role", message.role()).put("content", message.content());
        }
    }

    private static ArrayNode toolsForResponses(List<McpToolDefinition> tools) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (McpToolDefinition tool : tools) {
            ObjectNode node = array.addObject();
            node.put("type", "function");
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.set("parameters", JSON.valueToTree(tool.inputSchema()));
            node.put("strict", false);
        }
        return array;
    }

    private static ArrayNode toolsForAnthropic(List<McpToolDefinition> tools) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (McpToolDefinition tool : tools) {
            ObjectNode node = array.addObject();
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.set("input_schema", JSON.valueToTree(tool.inputSchema()));
        }
        return array;
    }

    private static boolean hasFunctionCall(JsonNode output) {
        if (!output.isArray()) {
            return false;
        }
        for (JsonNode item : output) {
            if ("function_call".equals(item.path("type").asText(""))) {
                return true;
            }
        }
        return false;
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
