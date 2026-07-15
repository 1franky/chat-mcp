package com.aidatachat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aidatachat.application.port.in.ChatUseCase.GenerationEvent;
import com.aidatachat.application.port.in.ChatUseCase.GenerationSession;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.application.service.ChatService;
import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.ProviderType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("40e76e01-d43f-4caf-aa9d-d4d997d451e8");
    private static final UUID CONVERSATION_ID =
            UUID.fromString("b987fb7a-2231-4cc0-b53c-bcc3c71c863a");
    private static final UUID PROVIDER_ID = UUID.fromString("7d929830-1a50-4321-93ef-2e85dc0d9032");
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final LlmChatGateway llm = mock(LlmChatGateway.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final Conversation conversation =
            new Conversation(
                    CONVERSATION_ID, OWNER_ID, "Prueba", PROVIDER_ID, "fake-chat-v1", 0, NOW, NOW);
    private ChatService service;
    private ConversationMessage lastPersisted;

    @BeforeEach
    void setUp() {
        service =
                new ChatService(
                        conversations,
                        llm,
                        audit,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        20,
                        20_000,
                        20_000);
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
                        eq(ProviderType.FAKE),
                        eq("fake-chat-v1"),
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
                            lastPersisted =
                                    new ConversationMessage(
                                            invocation.getArgument(2),
                                            CONVERSATION_ID,
                                            2,
                                            MessageRole.ASSISTANT,
                                            invocation.getArgument(3),
                                            PROVIDER_ID,
                                            ProviderType.FAKE,
                                            "fake-chat-v1",
                                            invocation.getArgument(4),
                                            invocation.getArgument(5),
                                            invocation.getArgument(6),
                                            invocation.getArgument(7),
                                            invocation.getArgument(8),
                                            null,
                                            0,
                                            NOW,
                                            NOW);
                            return lastPersisted;
                        });
    }

    @Test
    void completesAndPersistsNormalizedUsage() {
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.FAKE,
                                "fake-chat-v1",
                                finitePublisher(
                                        LlmChunk.delta("hola", "request-1"),
                                        LlmChunk.completed(3, 5, "stop", "request-1"))));
        EventSubscriber subscriber = new EventSubscriber();

        service.startGeneration(OWNER_ID, CONVERSATION_ID, "pregunta", "127.0.0.1")
                .events()
                .subscribe(subscriber);

        assertThat(subscriber.events)
                .extracting(GenerationEvent::type)
                .containsExactly("generation", "delta", "complete");
        assertThat(subscriber.events.getLast().assistantMessage())
                .satisfies(
                        message -> {
                            assertThat(message.content()).isEqualTo("hola");
                            assertThat(message.status()).isEqualTo(MessageStatus.COMPLETED);
                            assertThat(message.inputTokens()).isEqualTo(3);
                            assertThat(message.outputTokens()).isEqualTo(5);
                            assertThat(message.providerRequestId()).isEqualTo("request-1");
                        });
        assertThat(subscriber.completed).isTrue();
    }

    @Test
    void subscriberCancellationMarksBrowserDisconnect() {
        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.FAKE,
                                "fake-chat-v1",
                                waitingPublisher(
                                        LlmChunk.delta("visible", null), upstreamCancelled)));
        EventSubscriber subscriber = new EventSubscriber();

        service.startGeneration(OWNER_ID, CONVERSATION_ID, "pregunta", "127.0.0.1")
                .events()
                .subscribe(subscriber);
        subscriber.cancelStream();

        assertThat(upstreamCancelled).isTrue();
        assertThat(lastPersisted.content()).isEqualTo("visible");
        assertThat(lastPersisted.status()).isEqualTo(MessageStatus.CANCELLED);
        assertThat(lastPersisted.finishReason()).isEqualTo("CLIENT_DISCONNECTED");
    }

    @Test
    void explicitCancellationCancelsUpstreamAndPreservesPartial() {
        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.FAKE,
                                "fake-chat-v1",
                                waitingPublisher(
                                        LlmChunk.delta("parcial", null), upstreamCancelled)));
        EventSubscriber subscriber = new EventSubscriber();
        GenerationSession session =
                service.startGeneration(OWNER_ID, CONVERSATION_ID, "pregunta", "127.0.0.1");
        session.events().subscribe(subscriber);

        service.cancelGeneration(OWNER_ID, CONVERSATION_ID, session.generationId(), "127.0.0.1");

        assertThat(upstreamCancelled).isTrue();
        assertThat(subscriber.events)
                .extracting(GenerationEvent::type)
                .containsExactly("generation", "delta", "cancelled");
        assertThat(subscriber.events.getLast().assistantMessage())
                .satisfies(
                        message -> {
                            assertThat(message.content()).isEqualTo("parcial");
                            assertThat(message.status()).isEqualTo(MessageStatus.CANCELLED);
                        });
        assertThat(subscriber.completed).isTrue();
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
                assistant ? ProviderType.FAKE : null,
                assistant ? "fake-chat-v1" : null,
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

    private Flow.Publisher<LlmChunk> waitingPublisher(
            LlmChunk first, AtomicBoolean upstreamCancelled) {
        return subscriber ->
                subscriber.onSubscribe(
                        new Flow.Subscription() {
                            private boolean emitted;

                            @Override
                            public void request(long count) {
                                if (!emitted) {
                                    emitted = true;
                                    subscriber.onNext(first);
                                }
                            }

                            @Override
                            public void cancel() {
                                upstreamCancelled.set(true);
                            }
                        });
    }

    private static final class EventSubscriber implements Flow.Subscriber<GenerationEvent> {

        private final List<GenerationEvent> events = new ArrayList<>();
        private boolean completed;
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(GenerationEvent item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        private void cancelStream() {
            subscription.cancel();
        }
    }
}
