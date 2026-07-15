package com.aidatachat.application.service;

import com.aidatachat.application.exception.ChatConflictException;
import com.aidatachat.application.exception.ConversationNotFoundException;
import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.port.in.ChatUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.application.port.out.LlmChatGateway;
import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatService implements ChatUseCase {

    private static final String DEFAULT_TITLE = "Nueva conversacion";
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_MESSAGE_LENGTH = 32_000;

    private final ConversationRepository conversations;
    private final LlmChatGateway llm;
    private final AuditRepository audit;
    private final Clock clock;
    private final int maxHistoryMessages;
    private final int maxHistoryCharacters;
    private final int maxResponseCharacters;
    private final Map<UUID, GenerationState> activeById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeByConversation = new ConcurrentHashMap<>();

    public ChatService(
            ConversationRepository conversations,
            LlmChatGateway llm,
            AuditRepository audit,
            Clock clock,
            int maxHistoryMessages,
            int maxHistoryCharacters,
            int maxResponseCharacters) {
        this.conversations = Objects.requireNonNull(conversations);
        this.llm = Objects.requireNonNull(llm);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.maxHistoryMessages = positive(maxHistoryMessages, "maxHistoryMessages");
        this.maxHistoryCharacters = positive(maxHistoryCharacters, "maxHistoryCharacters");
        this.maxResponseCharacters = positive(maxResponseCharacters, "maxResponseCharacters");
    }

    @Override
    public ConversationPageView listConversations(UUID ownerId, String query, int page, int size) {
        UUID owner = requireId(ownerId, "ownerId");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        String normalizedQuery = query == null ? "" : query.strip();
        if (normalizedQuery.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Search query is too long");
        }
        ConversationRepository.ConversationPage result =
                conversations.findAllByOwnerId(owner, normalizedQuery, page, size);
        return new ConversationPageView(
                result.items().stream().map(this::toView).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @Override
    public ConversationView getConversation(UUID ownerId, UUID conversationId) {
        return toView(findOwned(ownerId, conversationId));
    }

    @Override
    public ConversationView createConversation(UUID ownerId, CreateConversationCommand command) {
        UUID owner = requireId(ownerId, "ownerId");
        Objects.requireNonNull(command, "command is required");
        UUID providerConnectionId =
                requireId(command.providerConnectionId(), "providerConnectionId");
        String modelId = required(command.modelId(), 255, "modelId");
        llm.validateSelection(owner, providerConnectionId, modelId);
        Instant now = clock.instant();
        Conversation saved =
                conversations.save(
                        new Conversation(
                                UUID.randomUUID(),
                                owner,
                                optionalTitle(command.title()),
                                providerConnectionId,
                                modelId,
                                0,
                                now,
                                now));
        appendAudit(
                owner, saved.id(), "CONVERSATION_CREATED", true, command.remoteAddress(), Map.of());
        return toView(saved);
    }

    @Override
    public ConversationView renameConversation(
            UUID ownerId, UUID conversationId, String title, String remoteAddress) {
        Conversation current = findOwned(ownerId, conversationId);
        Instant now = clock.instant();
        Conversation saved =
                conversations.save(
                        new Conversation(
                                current.id(),
                                current.ownerId(),
                                required(title, MAX_TITLE_LENGTH, "title"),
                                current.providerConnectionId(),
                                current.modelId(),
                                current.version(),
                                current.createdAt(),
                                now));
        appendAudit(
                current.ownerId(),
                current.id(),
                "CONVERSATION_RENAMED",
                true,
                remoteAddress,
                Map.of());
        return toView(saved);
    }

    @Override
    public ConversationView selectModel(
            UUID ownerId,
            UUID conversationId,
            UUID providerConnectionId,
            String modelId,
            String remoteAddress) {
        Conversation current = findOwned(ownerId, conversationId);
        if (activeByConversation.containsKey(current.id())) {
            throw new ChatConflictException("Cannot change model during an active generation");
        }
        UUID providerId = requireId(providerConnectionId, "providerConnectionId");
        String selectedModel = required(modelId, 255, "modelId");
        llm.validateSelection(current.ownerId(), providerId, selectedModel);
        Conversation saved =
                conversations.save(
                        new Conversation(
                                current.id(),
                                current.ownerId(),
                                current.title(),
                                providerId,
                                selectedModel,
                                current.version(),
                                current.createdAt(),
                                clock.instant()));
        appendAudit(
                current.ownerId(),
                current.id(),
                "CONVERSATION_MODEL_SELECTED",
                true,
                remoteAddress,
                Map.of("model_id", selectedModel));
        return toView(saved);
    }

    @Override
    public void deleteConversation(UUID ownerId, UUID conversationId, String remoteAddress) {
        Conversation current = findOwned(ownerId, conversationId);
        UUID generationId = activeByConversation.get(current.id());
        if (generationId != null) {
            GenerationState state = activeById.get(generationId);
            if (state != null) {
                state.cancel("CONVERSATION_DELETED");
            }
        }
        conversations.deleteByIdAndOwnerId(current.id(), current.ownerId());
        appendAudit(
                current.ownerId(),
                current.id(),
                "CONVERSATION_DELETED",
                true,
                remoteAddress,
                Map.of());
    }

    @Override
    public List<MessageView> listMessages(UUID ownerId, UUID conversationId) {
        findOwned(ownerId, conversationId);
        return conversations.findMessages(conversationId, ownerId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public GenerationSession startGeneration(
            UUID ownerId, UUID conversationId, String content, String remoteAddress) {
        Conversation conversation = findOwned(ownerId, conversationId);
        String userContent = required(content, MAX_MESSAGE_LENGTH, "content");
        List<ConversationMessage> existing =
                conversations.findMessages(conversation.id(), conversation.ownerId());
        List<ChatMessage> prompt = prompt(existing, null);
        prompt.add(new ChatMessage("user", userContent));
        LlmChatGateway.ChatStream stream = stream(conversation, prompt);
        Instant now = clock.instant();
        ConversationRepository.GenerationMessages created =
                conversations.createGeneration(
                        conversation.id(),
                        conversation.ownerId(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        userContent,
                        conversation.providerConnectionId(),
                        stream.providerType(),
                        stream.modelId(),
                        now);
        return register(
                conversation,
                created.userMessage(),
                created.assistantMessage(),
                stream.publisher(),
                remoteAddress);
    }

    @Override
    public GenerationSession regenerate(
            UUID ownerId, UUID conversationId, UUID sourceMessageId, String remoteAddress) {
        Conversation conversation = findOwned(ownerId, conversationId);
        List<ConversationMessage> existing =
                conversations.findMessages(conversation.id(), conversation.ownerId());
        ConversationMessage source =
                existing.stream()
                        .filter(message -> message.id().equals(sourceMessageId))
                        .filter(message -> message.role() == MessageRole.ASSISTANT)
                        .findFirst()
                        .orElseThrow(ConversationNotFoundException::new);
        List<ChatMessage> prompt = prompt(existing, source.position());
        if (prompt.isEmpty() || !"user".equals(prompt.getLast().role())) {
            throw new ChatConflictException("The response cannot be regenerated without a prompt");
        }
        LlmChatGateway.ChatStream stream = stream(conversation, prompt);
        ConversationMessage assistant =
                conversations.createRegeneration(
                        conversation.id(),
                        conversation.ownerId(),
                        UUID.randomUUID(),
                        source.id(),
                        conversation.providerConnectionId(),
                        stream.providerType(),
                        stream.modelId(),
                        clock.instant());
        return register(conversation, null, assistant, stream.publisher(), remoteAddress);
    }

    @Override
    public void cancelGeneration(
            UUID ownerId, UUID conversationId, UUID generationId, String remoteAddress) {
        UUID owner = requireId(ownerId, "ownerId");
        requireId(conversationId, "conversationId");
        GenerationState state = activeById.get(requireId(generationId, "generationId"));
        if (state == null
                || !state.ownerId.equals(owner)
                || !state.conversationId.equals(conversationId)) {
            throw new ConversationNotFoundException();
        }
        state.cancel("USER_CANCELLED");
        appendAudit(
                owner,
                conversationId,
                "GENERATION_CANCELLED",
                true,
                remoteAddress,
                Map.of("generation_id", generationId.toString()));
    }

    private GenerationSession register(
            Conversation conversation,
            ConversationMessage userMessage,
            ConversationMessage assistantMessage,
            Flow.Publisher<LlmChunk> upstream,
            String remoteAddress) {
        UUID generationId = UUID.randomUUID();
        GenerationState state =
                new GenerationState(
                        generationId,
                        conversation.ownerId(),
                        conversation.id(),
                        userMessage,
                        assistantMessage,
                        upstream,
                        remoteAddress);
        UUID conflict = activeByConversation.putIfAbsent(conversation.id(), generationId);
        if (conflict != null) {
            conversations.updateAssistantMessage(
                    conversation.id(),
                    conversation.ownerId(),
                    assistantMessage.id(),
                    "",
                    MessageStatus.CANCELLED,
                    null,
                    null,
                    "CONCURRENT_GENERATION",
                    null,
                    clock.instant());
            throw new ChatConflictException("A generation is already active");
        }
        activeById.put(generationId, state);
        appendAudit(
                conversation.ownerId(),
                conversation.id(),
                "GENERATION_STARTED",
                true,
                remoteAddress,
                Map.of("generation_id", generationId.toString()));
        return new GenerationSession(generationId, new GenerationPublisher(state));
    }

    private LlmChatGateway.ChatStream stream(Conversation conversation, List<ChatMessage> prompt) {
        if (conversation.providerConnectionId() == null) {
            throw new ChatConflictException("The conversation provider is no longer available");
        }
        return llm.stream(
                conversation.ownerId(),
                conversation.providerConnectionId(),
                conversation.modelId(),
                List.copyOf(prompt));
    }

    private List<ChatMessage> prompt(List<ConversationMessage> messages, Long beforePosition) {
        List<ChatMessage> candidates =
                messages.stream()
                        .filter(
                                message ->
                                        beforePosition == null
                                                || message.position() < beforePosition)
                        .filter(
                                message ->
                                        message.role() == MessageRole.USER
                                                || message.status() == MessageStatus.COMPLETED)
                        .map(
                                message ->
                                        new ChatMessage(
                                                message.role() == MessageRole.USER
                                                        ? "user"
                                                        : "assistant",
                                                message.content()))
                        .toList();
        List<ChatMessage> selected = new ArrayList<>();
        int characters = 0;
        for (int index = candidates.size() - 1;
                index >= 0 && selected.size() < maxHistoryMessages;
                index--) {
            ChatMessage candidate = candidates.get(index);
            if (!selected.isEmpty()
                    && characters + candidate.content().length() > maxHistoryCharacters) {
                break;
            }
            selected.addFirst(candidate);
            characters += candidate.content().length();
        }
        return selected;
    }

    private Conversation findOwned(UUID ownerId, UUID conversationId) {
        return conversations
                .findByIdAndOwnerId(
                        requireId(conversationId, "conversationId"), requireId(ownerId, "ownerId"))
                .orElseThrow(ConversationNotFoundException::new);
    }

    private ConversationView toView(Conversation conversation) {
        return new ConversationView(
                conversation.id(),
                conversation.title(),
                conversation.providerConnectionId(),
                conversation.modelId(),
                conversation.createdAt(),
                conversation.updatedAt());
    }

    private MessageView toView(ConversationMessage message) {
        return new MessageView(
                message.id(),
                message.conversationId(),
                message.position(),
                message.role(),
                message.content(),
                message.providerConnectionId(),
                message.providerType(),
                message.modelId(),
                message.status(),
                message.inputTokens(),
                message.outputTokens(),
                message.finishReason(),
                message.providerRequestId(),
                message.regeneratedFromMessageId(),
                message.createdAt(),
                message.updatedAt());
    }

    private String optionalTitle(String title) {
        return title == null || title.isBlank()
                ? DEFAULT_TITLE
                : required(title, MAX_TITLE_LENGTH, "title");
    }

    private String required(String value, int maxLength, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " length is invalid");
        }
        return normalized;
    }

    private UUID requireId(UUID value, String field) {
        return Objects.requireNonNull(value, field + " is required");
    }

    private int positive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private void appendAudit(
            UUID actorId,
            UUID targetId,
            String event,
            boolean success,
            String remoteAddress,
            Map<String, String> metadata) {
        audit.append(
                new AuditRepository.AuditEvent(
                        actorId,
                        targetId,
                        event,
                        success,
                        clock.instant(),
                        remoteAddress,
                        Map.copyOf(metadata)));
    }

    private final class GenerationState implements Flow.Subscriber<LlmChunk> {

        private final UUID generationId;
        private final UUID ownerId;
        private final UUID conversationId;
        private final ConversationMessage userMessage;
        private final UUID assistantMessageId;
        private final Flow.Publisher<LlmChunk> upstream;
        private final String remoteAddress;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final StringBuilder content = new StringBuilder();
        private Flow.Subscriber<? super GenerationEvent> downstream;
        private Flow.Subscription upstreamSubscription;
        private ConversationMessage assistantMessage;
        private Integer inputTokens;
        private Integer outputTokens;
        private String finishReason;
        private String providerRequestId;

        private GenerationState(
                UUID generationId,
                UUID ownerId,
                UUID conversationId,
                ConversationMessage userMessage,
                ConversationMessage assistantMessage,
                Flow.Publisher<LlmChunk> upstream,
                String remoteAddress) {
            this.generationId = generationId;
            this.ownerId = ownerId;
            this.conversationId = conversationId;
            this.userMessage = userMessage;
            this.assistantMessageId = assistantMessage.id();
            this.assistantMessage = assistantMessage;
            this.upstream = upstream;
            this.remoteAddress = remoteAddress;
        }

        private synchronized void attach(Flow.Subscriber<? super GenerationEvent> subscriber) {
            if (downstream != null) {
                subscriber.onError(new IllegalStateException("Generation supports one subscriber"));
                return;
            }
            downstream = subscriber;
        }

        private synchronized void start() {
            if (terminal.get()) {
                return;
            }
            emit(GenerationEvent.started(generationId, view(userMessage), view(assistantMessage)));
            upstream.subscribe(this);
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription subscription) {
            if (terminal.get()) {
                subscription.cancel();
                return;
            }
            upstreamSubscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public synchronized void onNext(LlmChunk chunk) {
            if (terminal.get()) {
                return;
            }
            if (content.length() + chunk.content().length() > maxResponseCharacters) {
                fail("PROVIDER_RESPONSE_TOO_LARGE", false, null);
                return;
            }
            content.append(chunk.content());
            if (chunk.inputTokens() != null) {
                inputTokens = chunk.inputTokens();
            }
            if (chunk.outputTokens() != null) {
                outputTokens = chunk.outputTokens();
            }
            if (chunk.finishReason() != null) {
                finishReason = bounded(chunk.finishReason(), 120);
            }
            if (chunk.providerRequestId() != null) {
                providerRequestId = bounded(chunk.providerRequestId(), 200);
            }
            assistantMessage = persist(MessageStatus.STREAMING, null);
            if (!chunk.content().isEmpty()) {
                emit(GenerationEvent.delta(generationId, view(assistantMessage), chunk.content()));
            }
            if (chunk.finished()) {
                complete();
            }
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            if (throwable instanceof ProviderCommunicationException providerFailure) {
                if (providerFailure.providerRequestId() != null) {
                    providerRequestId = bounded(providerFailure.providerRequestId(), 200);
                }
                fail(providerFailure.code(), providerFailure.retryable(), throwable);
            } else {
                fail("PROVIDER_STREAM_FAILED", true, throwable);
            }
        }

        @Override
        public synchronized void onComplete() {
            complete();
        }

        private synchronized void complete() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            assistantMessage =
                    persist(MessageStatus.COMPLETED, finishReason == null ? "stop" : finishReason);
            emit(
                    GenerationEvent.terminal(
                            "complete", generationId, view(assistantMessage), null, false));
            finishDownstream();
            onTerminal(true, null);
        }

        private synchronized void cancel(String reason) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            if (upstreamSubscription != null) {
                upstreamSubscription.cancel();
            }
            assistantMessage = persist(MessageStatus.CANCELLED, bounded(reason, 120));
            emit(
                    GenerationEvent.terminal(
                            "cancelled", generationId, view(assistantMessage), reason, false));
            finishDownstream();
            onTerminal(false, reason);
        }

        private synchronized void fail(String code, boolean retryable, Throwable throwable) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            if (upstreamSubscription != null) {
                upstreamSubscription.cancel();
            }
            assistantMessage = persist(MessageStatus.FAILED, bounded(code, 120));
            emit(
                    GenerationEvent.terminal(
                            "error", generationId, view(assistantMessage), code, retryable));
            finishDownstream();
            onTerminal(false, code);
        }

        private ConversationMessage persist(MessageStatus status, String terminalReason) {
            return conversations.updateAssistantMessage(
                    conversationId,
                    ownerId,
                    assistantMessageId,
                    content.toString(),
                    status,
                    inputTokens,
                    outputTokens,
                    terminalReason,
                    providerRequestId,
                    clock.instant());
        }

        private void emit(GenerationEvent event) {
            if (downstream != null) {
                downstream.onNext(event);
            }
        }

        private void finishDownstream() {
            if (downstream != null) {
                downstream.onComplete();
            }
        }

        private MessageView view(ConversationMessage message) {
            return message == null ? null : toView(message);
        }

        private String bounded(String value, int maxLength) {
            return value.length() <= maxLength ? value : value.substring(0, maxLength);
        }

        private void onTerminal(boolean success, String code) {
            activeById.remove(generationId, this);
            activeByConversation.remove(conversationId, generationId);
            appendAudit(
                    ownerId,
                    conversationId,
                    success ? "GENERATION_COMPLETED" : "GENERATION_FINISHED_INCOMPLETE",
                    success,
                    remoteAddress,
                    code == null ? Map.of() : Map.of("result_code", code));
        }
    }

    private static final class GenerationPublisher implements Flow.Publisher<GenerationEvent> {

        private final GenerationState state;

        private GenerationPublisher(GenerationState state) {
            this.state = state;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super GenerationEvent> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber is required");
            GenerationSubscription subscription = new GenerationSubscription(state);
            state.attach(subscriber);
            subscriber.onSubscribe(subscription);
        }
    }

    private static final class GenerationSubscription implements Flow.Subscription {

        private final GenerationState state;
        private final AtomicBoolean started = new AtomicBoolean();

        private GenerationSubscription(GenerationState state) {
            this.state = state;
        }

        @Override
        public void request(long count) {
            if (count <= 0) {
                state.fail("INVALID_STREAM_DEMAND", false, null);
                return;
            }
            if (started.compareAndSet(false, true)) {
                state.start();
            }
        }

        @Override
        public void cancel() {
            state.cancel("CLIENT_DISCONNECTED");
        }
    }
}
