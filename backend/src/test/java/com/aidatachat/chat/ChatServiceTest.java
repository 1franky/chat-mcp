package com.aidatachat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aidatachat.application.exception.DocumentNotFoundException;
import com.aidatachat.application.port.in.ChatUseCase.CitationView;
import com.aidatachat.application.port.in.ChatUseCase.GenerationEvent;
import com.aidatachat.application.port.in.ChatUseCase.GenerationSession;
import com.aidatachat.application.port.in.ChatUseCase.MessageView;
import com.aidatachat.application.port.in.RagRetrievalUseCase;
import com.aidatachat.application.port.in.RagRetrievalUseCase.RetrievalResult;
import com.aidatachat.application.port.in.RagRetrievalUseCase.RetrievedChunk;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.application.port.out.ConversationRepository.MessageDocumentEntry;
import com.aidatachat.application.port.out.ConversationRepository.MessageDocumentRef;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.application.port.out.McpGateway;
import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.application.service.ChatService;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentChunk;
import com.aidatachat.domain.model.DocumentStatus;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.McpConnectionStatus;
import com.aidatachat.domain.model.MessageDocumentRelation;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.ProviderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("40e76e01-d43f-4caf-aa9d-d4d997d451e8");
    private static final UUID CONVERSATION_ID =
            UUID.fromString("b987fb7a-2231-4cc0-b53c-bcc3c71c863a");
    private static final UUID PROVIDER_ID = UUID.fromString("7d929830-1a50-4321-93ef-2e85dc0d9032");
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

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
                    "fake-chat-v1",
                    List.of(),
                    0,
                    NOW,
                    NOW);
    private ChatService service;
    private ConversationMessage lastPersisted;

    @BeforeEach
    void setUp() {
        when(mcp.status())
                .thenReturn(
                        new McpConnectionStatus(
                                IntegrationState.DOWN,
                                "0.0.0",
                                "0.0.0",
                                "2025-11-25",
                                "test",
                                true));
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
                        6,
                        1_048_576,
                        20_000,
                        Duration.ofSeconds(5),
                        Executors.newSingleThreadExecutor());
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
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any(), any()))
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
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any(), any()))
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
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any(), any()))
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

    @Test
    void neverCallsRetrievalOrRecordsCitationsWhenNoDocumentsAreSelected() {
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any(), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.FAKE,
                                "fake-chat-v1",
                                finitePublisher(LlmChunk.completed(1, 1, "stop", "request-1"))));

        service.startGeneration(OWNER_ID, CONVERSATION_ID, "pregunta", "127.0.0.1")
                .events()
                .subscribe(new EventSubscriber());

        verifyNoInteractions(ragRetrieval);
        verify(conversations, never()).recordMessageDocuments(any(), any(), any(), any(), any());
    }

    @Test
    void injectsRetrievedContextIntoThePromptWithoutMutatingThePersistedUserMessage() {
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        Conversation withDocuments =
                new Conversation(
                        CONVERSATION_ID,
                        OWNER_ID,
                        "Prueba",
                        PROVIDER_ID,
                        "fake-chat-v1",
                        List.of(documentId),
                        0,
                        NOW,
                        NOW);
        when(conversations.findByIdAndOwnerId(CONVERSATION_ID, OWNER_ID))
                .thenReturn(Optional.of(withDocuments));
        when(conversations.createGeneration(
                        eq(CONVERSATION_ID),
                        eq(OWNER_ID),
                        any(UUID.class),
                        any(UUID.class),
                        eq("pregunta"),
                        eq(PROVIDER_ID),
                        eq(ProviderType.FAKE),
                        eq("fake-chat-v1"),
                        eq(NOW)))
                .thenAnswer(
                        invocation -> {
                            UUID userId = invocation.getArgument(2);
                            UUID assistantId = invocation.getArgument(3);
                            return new ConversationRepository.GenerationMessages(
                                    withDocuments,
                                    message(
                                            userId,
                                            1,
                                            MessageRole.USER,
                                            "pregunta",
                                            MessageStatus.COMPLETED),
                                    message(
                                            assistantId,
                                            2,
                                            MessageRole.ASSISTANT,
                                            "",
                                            MessageStatus.STREAMING));
                        });
        RetrievedChunk retrieved =
                new RetrievedChunk(
                        documentId, "informe.pdf", chunkId, 3, null, "contenido recuperado", 0.87);
        when(ragRetrieval.retrieve(OWNER_ID, List.of(documentId), "pregunta"))
                .thenReturn(new RetrievalResult(List.of(retrieved)));
        when(llm.stream(eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), any(), any()))
                .thenReturn(
                        new LlmChatGateway.ChatStream(
                                ProviderType.FAKE,
                                "fake-chat-v1",
                                finitePublisher(LlmChunk.completed(1, 1, "stop", "request-1"))));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);

        service.startGeneration(OWNER_ID, CONVERSATION_ID, "pregunta", "127.0.0.1")
                .events()
                .subscribe(new EventSubscriber());

        verify(llm).stream(
                eq(OWNER_ID), eq(PROVIDER_ID), eq("fake-chat-v1"), promptCaptor.capture(), any());
        String sentContent = promptCaptor.getValue().getLast().content();
        assertThat(sentContent).contains("contenido recuperado");
        assertThat(sentContent).contains("informe.pdf");
        assertThat(sentContent).contains("Pregunta del usuario:\npregunta");
        assertThat(sentContent).contains("nunca como instrucciones");

        ArgumentCaptor<List<MessageDocumentEntry>> entriesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(conversations)
                .recordMessageDocuments(
                        eq(CONVERSATION_ID),
                        eq(OWNER_ID),
                        any(UUID.class),
                        entriesCaptor.capture(),
                        eq(NOW));
        List<MessageDocumentEntry> entries = entriesCaptor.getValue();
        assertThat(entries)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.relation())
                                    .isEqualTo(MessageDocumentRelation.SELECTED);
                            assertThat(entry.documentId()).isEqualTo(documentId);
                            assertThat(entry.chunkId()).isNull();
                        });
        assertThat(entries)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.relation()).isEqualTo(MessageDocumentRelation.CITED);
                            assertThat(entry.chunkId()).isEqualTo(chunkId);
                            assertThat(entry.score()).isEqualTo(0.87);
                        });
    }

    @Test
    void selectDocumentsPersistsTheSelectionAfterValidatingOwnership() {
        UUID documentId = UUID.randomUUID();
        when(documents.findAllByIdsAndOwnerId(List.of(documentId), OWNER_ID))
                .thenReturn(
                        List.of(
                                new Document(
                                        documentId,
                                        OWNER_ID,
                                        "manual.pdf",
                                        "storage-key",
                                        "application/pdf",
                                        10,
                                        "a".repeat(64),
                                        DocumentStatus.READY,
                                        null,
                                        "fake-embedding-v1",
                                        1536,
                                        1,
                                        0,
                                        NOW,
                                        NOW)));
        Conversation updated =
                new Conversation(
                        CONVERSATION_ID,
                        OWNER_ID,
                        "Prueba",
                        PROVIDER_ID,
                        "fake-chat-v1",
                        List.of(documentId),
                        0,
                        NOW,
                        NOW);
        when(conversations.replaceSelectedDocuments(
                        CONVERSATION_ID, OWNER_ID, List.of(documentId), NOW))
                .thenReturn(updated);

        var view =
                service.selectDocuments(
                        OWNER_ID, CONVERSATION_ID, List.of(documentId), "127.0.0.1");

        assertThat(view.selectedDocumentIds()).containsExactly(documentId);
    }

    @Test
    void selectDocumentsRejectsADocumentNotOwnedByTheCaller() {
        UUID documentId = UUID.randomUUID();
        when(documents.findAllByIdsAndOwnerId(List.of(documentId), OWNER_ID)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.selectDocuments(
                                        OWNER_ID,
                                        CONVERSATION_ID,
                                        List.of(documentId),
                                        "127.0.0.1"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void hydratesCitationsWhenListingMessages() {
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        ConversationMessage assistantMessage =
                message(messageId, 2, MessageRole.ASSISTANT, "respuesta", MessageStatus.COMPLETED);
        when(conversations.findMessages(CONVERSATION_ID, OWNER_ID))
                .thenReturn(List.of(assistantMessage));
        when(conversations.findToolCallsForMessages(List.of(messageId))).thenReturn(Map.of());
        when(conversations.findCitationsForMessages(List.of(messageId)))
                .thenReturn(
                        Map.of(
                                messageId,
                                List.of(
                                        new MessageDocumentRef(
                                                documentId,
                                                chunkId,
                                                MessageDocumentRelation.CITED,
                                                0.9))));
        when(vectorSearch.findByIds(OWNER_ID, List.of(chunkId)))
                .thenReturn(
                        List.of(
                                new DocumentChunk(
                                        chunkId,
                                        documentId,
                                        OWNER_ID,
                                        0,
                                        "contenido citado",
                                        2,
                                        null,
                                        "fake-embedding-v1",
                                        new float[] {1f},
                                        NOW)));
        when(documents.findAllByIdsAndOwnerId(List.of(documentId), OWNER_ID))
                .thenReturn(
                        List.of(
                                new Document(
                                        documentId,
                                        OWNER_ID,
                                        "manual.pdf",
                                        "storage-key",
                                        "application/pdf",
                                        10,
                                        "a".repeat(64),
                                        DocumentStatus.READY,
                                        null,
                                        "fake-embedding-v1",
                                        1536,
                                        1,
                                        0,
                                        NOW,
                                        NOW)));

        List<MessageView> views = service.listMessages(OWNER_ID, CONVERSATION_ID);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().citations()).hasSize(1);
        CitationView citation = views.getFirst().citations().getFirst();
        assertThat(citation.documentId()).isEqualTo(documentId);
        assertThat(citation.documentName()).isEqualTo("manual.pdf");
        assertThat(citation.chunkId()).isEqualTo(chunkId);
        assertThat(citation.pageNumber()).isEqualTo(2);
        assertThat(citation.snippet()).isEqualTo("contenido citado");
        assertThat(citation.score()).isEqualTo(0.9);
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
