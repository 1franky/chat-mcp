package com.aidatachat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidatachat.application.port.in.ChatUseCase.GenerationEvent;
import com.aidatachat.application.port.in.RagRetrievalUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.application.service.ChatService;
import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.ConversationToolCall;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.LlmToolCallDelta;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.McpToolDefinition;
import com.aidatachat.domain.model.McpToolResult;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.MessageToolCallStatus;
import com.aidatachat.domain.model.ProviderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatServiceToolCallingTest {

    private static final UUID OWNER_ID = UUID.fromString("40e76e01-d43f-4caf-aa9d-d4d997d451e8");
    private static final UUID CONVERSATION_ID =
            UUID.fromString("b987fb7a-2231-4cc0-b53c-bcc3c71c863a");
    private static final UUID PROVIDER_ID = UUID.fromString("7d929830-1a50-4321-93ef-2e85dc0d9032");
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");
    private static final McpToolDefinition HEALTH_CHECK =
            new McpToolDefinition("health_check", "Check health", Map.of(), true);

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final VectorSearchPort vectorSearch = mock(VectorSearchPort.class);
    private final RagRetrievalUseCase ragRetrieval = mock(RagRetrievalUseCase.class);
    private final LlmChatGateway llm = mock(LlmChatGateway.class);
    private final McpGateway mcp = mock(McpGateway.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final Conversation conversation =
            new Conversation(
                    CONVERSATION_ID,
                    OWNER_ID,
                    "Prueba",
                    PROVIDER_ID,
                    "gpt-test",
                    List.of(),
                    0,
                    NOW,
                    NOW);
    private ChatService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatService(
                        conversations,
                        documents,
                        vectorSearch,
                        ragRetrieval,
                        llm,
                        mcp,
                        audit,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        20,
                        20_000,
                        20_000,
                        3,
                        1_048_576,
                        20_000,
                        Duration.ofSeconds(5),
                        Executors.newCachedThreadPool());
        when(mcp.status())
                .thenReturn(
                        new McpConnectionStatus(
                                IntegrationState.UP, "1.0.0", "1.0.0", "2025-11-25", "ok", true));
        when(mcp.discoverTools()).thenReturn(List.of(HEALTH_CHECK));

        when(conversations.findByIdAndOwnerId(CONVERSATION_ID, OWNER_ID))
                .thenReturn(Optional.of(conversation));
        when(conversations.findMessages(CONVERSATION_ID, OWNER_ID)).thenReturn(List.of());
        when(conversations.createGeneration(
                        eq(CONVERSATION_ID),
                        eq(OWNER_ID),
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        eq(PROVIDER_ID),
                        eq(ProviderType.OPENAI),
                        eq("gpt-test"),
                        eq(NOW)))
                .thenAnswer(
                        invocation -> {
                            UUID userId = invocation.getArgument(2);
                            UUID assistantId = invocation.getArgument(3);
                            return new ConversationRepository.GenerationMessages(
                                    conversation,
                                    message(
                                            userId,
                                            1,
                                            MessageRole.USER,
                                            invocation.getArgument(4),
                                            MessageStatus.COMPLETED),
                                    message(
                                            assistantId,
                                            2,
                                            MessageRole.ASSISTANT,
                                            "",
                                            MessageStatus.STREAMING));
                        });
        when(conversations.updateAssistantMessage(
                        eq(CONVERSATION_ID),
                        eq(OWNER_ID),
                        any(UUID.class),
                        anyString(),
                        any(MessageStatus.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(NOW)))
                .thenAnswer(
                        invocation -> {
                            MessageStatus status = invocation.getArgument(4);
                            return message(
                                    invocation.getArgument(2),
                                    2,
                                    MessageRole.ASSISTANT,
                                    invocation.getArgument(3),
                                    status);
                        });
        when(conversations.recordToolCall(
                        eq(CONVERSATION_ID),
                        eq(OWNER_ID),
                        any(UUID.class),
                        any(UUID.class),
                        anyInt(),
                        anyInt(),
                        anyString(),
                        any(),
                        anyMap(),
                        eq(MessageToolCallStatus.PENDING),
                        eq(NOW)))
                .thenAnswer(
                        invocation ->
                                new ConversationToolCall(
                                        invocation.getArgument(3),
                                        invocation.getArgument(2),
                                        invocation.getArgument(4),
                                        invocation.getArgument(5),
                                        invocation.getArgument(6),
                                        invocation.getArgument(7),
                                        invocation.getArgument(8),
                                        MessageToolCallStatus.PENDING,
                                        null,
                                        null,
                                        null,
                                        NOW,
                                        null,
                                        NOW,
                                        NOW));
        when(conversations.updateToolCallResult(
                        eq(CONVERSATION_ID),
                        eq(OWNER_ID),
                        any(UUID.class),
                        any(MessageToolCallStatus.class),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenAnswer(
                        invocation -> {
                            MessageToolCallStatus status = invocation.getArgument(3);
                            Boolean isError = invocation.getArgument(4);
                            Map<String, Object> result = invocation.getArgument(5);
                            String errorCode = invocation.getArgument(6);
                            boolean terminal =
                                    status != MessageToolCallStatus.PENDING
                                            && status != MessageToolCallStatus.RUNNING;
                            return new ConversationToolCall(
                                    invocation.getArgument(2),
                                    UUID.randomUUID(),
                                    1,
                                    0,
                                    "health_check",
                                    null,
                                    Map.of(),
                                    status,
                                    isError,
                                    result,
                                    errorCode,
                                    NOW,
                                    terminal ? NOW : null,
                                    NOW,
                                    NOW);
                        });
        when(conversations.findToolCallsForMessages(any())).thenReturn(Map.of());
    }

    @Test
    void completesAfterASuccessfulToolRound() throws Exception {
        LlmChunk toolCallStart =
                LlmChunk.toolCallDelta(
                        List.of(new LlmToolCallDelta(0, "call-1", "health_check", "{}")), "req-1");
        LlmChunk toolCallFinish = LlmChunk.completed(2, 0, "tool_calls", "req-1");
        LlmChunk finalAnswer = LlmChunk.delta("Todo esta bien.", "req-2");
        LlmChunk finalDone = LlmChunk.completed(5, 3, "stop", "req-2");

        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("gpt-test"), any(), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.OPENAI,
                                "gpt-test",
                                finitePublisher(toolCallStart, toolCallFinish)))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.OPENAI,
                                "gpt-test",
                                finitePublisher(finalAnswer, finalDone)));
        when(mcp.call(eq("health_check"), anyMap()))
                .thenReturn(
                        new McpToolResult(
                                "health_check", false, List.of("ok"), Map.of("status", "ok")));

        EventSubscriber subscriber = new EventSubscriber();
        service.startGeneration(OWNER_ID, CONVERSATION_ID, "revisa el estado", "127.0.0.1")
                .events()
                .subscribe(subscriber);

        assertThat(subscriber.terminal.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.events)
                .extracting(GenerationEvent::type)
                .contains("tool_call", "tool_result", "complete")
                .doesNotContain("error");
        GenerationEvent toolResult =
                subscriber.events.stream()
                        .filter(event -> "tool_result".equals(event.type()))
                        .findFirst()
                        .orElseThrow();
        assertThat(toolResult.toolCall().isError()).isFalse();
        assertThat(toolResult.toolCall().status()).isEqualTo("COMPLETED");
        GenerationEvent complete =
                subscriber.events.stream()
                        .filter(event -> "complete".equals(event.type()))
                        .findFirst()
                        .orElseThrow();
        assertThat(complete.assistantMessage().content()).isEqualTo("Todo esta bien.");
        assertThat(complete.assistantMessage().status()).isEqualTo(MessageStatus.COMPLETED);
        verify(mcp, times(1)).call(eq("health_check"), anyMap());
    }

    @Test
    void blocksToolCallsOutsideTheAllowlist() throws Exception {
        LlmChunk toolCallStart =
                LlmChunk.toolCallDelta(
                        List.of(
                                new LlmToolCallDelta(
                                        0, "call-1", "execute_read_query", "{\"sql\":\"DELETE\"}")),
                        "req-1");
        LlmChunk toolCallFinish = LlmChunk.completed(2, 0, "tool_calls", "req-1");
        LlmChunk finalAnswer = LlmChunk.delta("No puedo hacer eso.", "req-2");
        LlmChunk finalDone = LlmChunk.completed(5, 3, "stop", "req-2");

        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("gpt-test"), any(), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.OPENAI,
                                "gpt-test",
                                finitePublisher(toolCallStart, toolCallFinish)))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.OPENAI,
                                "gpt-test",
                                finitePublisher(finalAnswer, finalDone)));

        EventSubscriber subscriber = new EventSubscriber();
        service.startGeneration(OWNER_ID, CONVERSATION_ID, "borra los usuarios", "127.0.0.1")
                .events()
                .subscribe(subscriber);

        assertThat(subscriber.terminal.await(5, TimeUnit.SECONDS)).isTrue();
        GenerationEvent toolResult =
                subscriber.events.stream()
                        .filter(event -> "tool_result".equals(event.type()))
                        .findFirst()
                        .orElseThrow();
        assertThat(toolResult.toolCall().status()).isEqualTo("BLOCKED");
        assertThat(toolResult.toolCall().isError()).isTrue();
        assertThat(subscriber.events).extracting(GenerationEvent::type).contains("complete");
        verify(mcp, never()).call(anyString(), anyMap());
    }

    @Test
    void stopsAfterMaxToolRoundsExceeded() throws Exception {
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("gpt-test"), any(), any()))
                .thenAnswer(
                        invocation ->
                                new LlmChatGateway.ChatStream(
                                        ProviderType.OPENAI,
                                        "gpt-test",
                                        finitePublisher(
                                                LlmChunk.toolCallDelta(
                                                        List.of(
                                                                new LlmToolCallDelta(
                                                                        0,
                                                                        "call-" + UUID.randomUUID(),
                                                                        "health_check",
                                                                        "{}")),
                                                        "req"),
                                                LlmChunk.completed(1, 1, "tool_calls", "req"))));
        when(mcp.call(eq("health_check"), anyMap()))
                .thenReturn(
                        new McpToolResult(
                                "health_check", false, List.of("ok"), Map.of("status", "ok")));

        EventSubscriber subscriber = new EventSubscriber();
        service.startGeneration(OWNER_ID, CONVERSATION_ID, "repite", "127.0.0.1")
                .events()
                .subscribe(subscriber);

        assertThat(subscriber.terminal.await(5, TimeUnit.SECONDS)).isTrue();
        GenerationEvent error =
                subscriber.events.stream()
                        .filter(event -> "error".equals(event.type()))
                        .findFirst()
                        .orElseThrow();
        assertThat(error.errorCode()).isEqualTo("MCP_TOOL_ROUNDS_EXCEEDED");
        assertThat(error.retryable()).isFalse();
    }

    private ConversationMessage message(
            UUID id, long position, MessageRole role, String content, MessageStatus status) {
        boolean assistant = role == MessageRole.ASSISTANT;
        return new ConversationMessage(
                id,
                CONVERSATION_ID,
                position,
                role,
                content,
                assistant ? PROVIDER_ID : null,
                assistant ? ProviderType.OPENAI : null,
                assistant ? "gpt-test" : null,
                status,
                null,
                null,
                null,
                null,
                null,
                0,
                NOW,
                NOW);
    }

    private Flow.Publisher<LlmChunk> finitePublisher(LlmChunk... chunks) {
        return subscriber ->
                subscriber.onSubscribe(
                        new Flow.Subscription() {
                            private boolean emitted;

                            @Override
                            public void request(long count) {
                                if (emitted) {
                                    return;
                                }
                                emitted = true;
                                for (LlmChunk chunk : chunks) {
                                    subscriber.onNext(chunk);
                                }
                                subscriber.onComplete();
                            }

                            @Override
                            public void cancel() {}
                        });
    }

    private static final class EventSubscriber implements Flow.Subscriber<GenerationEvent> {

        private final List<GenerationEvent> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(GenerationEvent item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }
    }
}
