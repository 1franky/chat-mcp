package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.ConversationToolCall;
import com.aidatachat.domain.model.MessageDocumentRelation;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.MessageToolCallStatus;
import com.aidatachat.domain.model.ProviderType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {

    ConversationPage findAllByOwnerId(UUID ownerId, String query, int page, int size);

    Optional<Conversation> findByIdAndOwnerId(UUID conversationId, UUID ownerId);

    Conversation save(Conversation conversation);

    void deleteByIdAndOwnerId(UUID conversationId, UUID ownerId);

    List<ConversationMessage> findMessages(UUID conversationId, UUID ownerId);

    GenerationMessages createGeneration(
            UUID conversationId,
            UUID ownerId,
            UUID userMessageId,
            UUID assistantMessageId,
            String userContent,
            UUID providerConnectionId,
            ProviderType providerType,
            String modelId,
            Instant createdAt);

    ConversationMessage createRegeneration(
            UUID conversationId,
            UUID ownerId,
            UUID assistantMessageId,
            UUID regeneratedFromMessageId,
            UUID providerConnectionId,
            ProviderType providerType,
            String modelId,
            Instant createdAt);

    ConversationMessage updateAssistantMessage(
            UUID conversationId,
            UUID ownerId,
            UUID messageId,
            String content,
            MessageStatus status,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId,
            Instant updatedAt);

    ConversationToolCall recordToolCall(
            UUID conversationId,
            UUID ownerId,
            UUID messageId,
            UUID toolCallId,
            int generationRound,
            int sequence,
            String toolName,
            String providerToolCallId,
            Map<String, Object> arguments,
            MessageToolCallStatus status,
            Instant startedAt);

    ConversationToolCall updateToolCallResult(
            UUID conversationId,
            UUID ownerId,
            UUID toolCallId,
            MessageToolCallStatus status,
            Boolean isError,
            Map<String, Object> result,
            String errorCode,
            Instant completedAt,
            Instant updatedAt);

    Map<UUID, List<ConversationToolCall>> findToolCallsForMessages(Collection<UUID> messageIds);

    /** Delete-then-insert, mirroring {@code VectorSearchPort.replaceChunks}'s idempotency. */
    Conversation replaceSelectedDocuments(
            UUID conversationId, UUID ownerId, List<UUID> documentIds, Instant updatedAt);

    void recordMessageDocuments(
            UUID conversationId,
            UUID ownerId,
            UUID messageId,
            List<MessageDocumentEntry> entries,
            Instant createdAt);

    Map<UUID, List<MessageDocumentRef>> findCitationsForMessages(Collection<UUID> messageIds);

    record MessageDocumentEntry(
            UUID documentId, UUID chunkId, MessageDocumentRelation relation, Double score) {}

    record MessageDocumentRef(
            UUID documentId, UUID chunkId, MessageDocumentRelation relation, Double score) {}

    record ConversationPage(
            List<Conversation> items, int page, int size, long totalElements, int totalPages) {

        public ConversationPage {
            items = List.copyOf(items);
        }
    }

    record GenerationMessages(
            Conversation conversation,
            ConversationMessage userMessage,
            ConversationMessage assistantMessage) {}
}
