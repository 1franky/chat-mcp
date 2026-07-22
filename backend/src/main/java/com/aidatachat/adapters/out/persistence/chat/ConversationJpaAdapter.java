package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.application.exception.ChatConflictException;
import com.aidatachat.application.exception.ConversationNotFoundException;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.ConversationToolCall;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.MessageToolCallStatus;
import com.aidatachat.domain.model.ProviderType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConversationJpaAdapter implements ConversationRepository {

    private final SpringDataConversationRepository conversations;
    private final SpringDataConversationMessageRepository messages;
    private final SpringDataConversationMessageToolCallRepository toolCalls;
    private final SpringDataMessageDocumentRepository messageDocuments;
    private final JdbcTemplate jdbcTemplate;

    public ConversationJpaAdapter(
            SpringDataConversationRepository conversations,
            SpringDataConversationMessageRepository messages,
            SpringDataConversationMessageToolCallRepository toolCalls,
            SpringDataMessageDocumentRepository messageDocuments,
            JdbcTemplate jdbcTemplate) {
        this.conversations = conversations;
        this.messages = messages;
        this.toolCalls = toolCalls;
        this.messageDocuments = messageDocuments;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationPage findAllByOwnerId(UUID ownerId, String query, int page, int size) {
        Page<ConversationEntity> result =
                conversations.findOwned(
                        ownerId,
                        query,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        Map<UUID, List<UUID>> selectedDocumentsByConversation =
                selectedDocumentIdsByConversation(
                        result.getContent().stream().map(ConversationEntity::getId).toList());
        return new ConversationPage(
                result.getContent().stream()
                        .map(
                                entity ->
                                        entity.toDomain(
                                                selectedDocumentsByConversation.getOrDefault(
                                                        entity.getId(), List.of())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findByIdAndOwnerId(UUID conversationId, UUID ownerId) {
        return conversations
                .findByIdAndOwnerId(conversationId, ownerId)
                .map(entity -> entity.toDomain(selectedDocumentIds(conversationId)));
    }

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        ConversationEntity entity =
                conversations
                        .findByIdAndOwnerId(conversation.id(), conversation.ownerId())
                        .map(
                                current -> {
                                    current.update(conversation);
                                    return current;
                                })
                        .orElseGet(() -> new ConversationEntity(conversation));
        ConversationEntity saved = conversations.saveAndFlush(entity);
        return saved.toDomain(selectedDocumentIds(saved.getId()));
    }

    @Override
    @Transactional
    public void deleteByIdAndOwnerId(UUID conversationId, UUID ownerId) {
        conversations.findByIdAndOwnerId(conversationId, ownerId).ifPresent(conversations::delete);
        conversations.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessage> findMessages(UUID conversationId, UUID ownerId) {
        if (!conversations.existsByIdAndOwnerId(conversationId, ownerId)) {
            throw new ConversationNotFoundException();
        }
        return messages.findAllByConversationIdOrderByPositionAscIdAsc(conversationId).stream()
                .map(ConversationMessageEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public GenerationMessages createGeneration(
            UUID conversationId,
            UUID ownerId,
            UUID userMessageId,
            UUID assistantMessageId,
            String userContent,
            UUID providerConnectionId,
            ProviderType providerType,
            String modelId,
            Instant createdAt) {
        ConversationEntity conversation = lockConversation(conversationId, ownerId);
        rejectActiveGeneration(conversationId);
        long nextPosition = messages.maxPosition(conversationId) + 1;
        ConversationMessage user =
                new ConversationMessage(
                        userMessageId,
                        conversationId,
                        nextPosition,
                        MessageRole.USER,
                        userContent,
                        null,
                        null,
                        null,
                        MessageStatus.COMPLETED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        createdAt,
                        createdAt);
        ConversationMessage assistant =
                assistantMessage(
                        assistantMessageId,
                        conversationId,
                        nextPosition + 1,
                        providerConnectionId,
                        providerType,
                        modelId,
                        null,
                        createdAt);
        List<ConversationMessageEntity> saved =
                messages.saveAllAndFlush(
                        List.of(
                                new ConversationMessageEntity(user),
                                new ConversationMessageEntity(assistant)));
        conversation.touch(createdAt);
        conversations.flush();
        return new GenerationMessages(
                conversation.toDomain(selectedDocumentIds(conversationId)),
                saved.get(0).toDomain(),
                saved.get(1).toDomain());
    }

    @Override
    @Transactional
    public ConversationMessage createRegeneration(
            UUID conversationId,
            UUID ownerId,
            UUID assistantMessageId,
            UUID regeneratedFromMessageId,
            UUID providerConnectionId,
            ProviderType providerType,
            String modelId,
            Instant createdAt) {
        ConversationEntity conversation = lockConversation(conversationId, ownerId);
        rejectActiveGeneration(conversationId);
        ConversationMessage source =
                messages.findByIdAndConversationId(regeneratedFromMessageId, conversationId)
                        .map(ConversationMessageEntity::toDomain)
                        .filter(message -> message.role() == MessageRole.ASSISTANT)
                        .orElseThrow(ConversationNotFoundException::new);
        long nextPosition = messages.maxPosition(conversationId) + 1;
        ConversationMessage assistant =
                assistantMessage(
                        assistantMessageId,
                        conversationId,
                        nextPosition,
                        providerConnectionId,
                        providerType,
                        modelId,
                        source.id(),
                        createdAt);
        ConversationMessage saved =
                messages.saveAndFlush(new ConversationMessageEntity(assistant)).toDomain();
        conversation.touch(createdAt);
        conversations.flush();
        return saved;
    }

    @Override
    @Transactional
    public ConversationMessage updateAssistantMessage(
            UUID conversationId,
            UUID ownerId,
            UUID messageId,
            String content,
            MessageStatus status,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId,
            Instant updatedAt) {
        ConversationEntity conversation = lockConversation(conversationId, ownerId);
        ConversationMessageEntity message =
                messages.findByIdAndConversationId(messageId, conversationId)
                        .filter(entity -> entity.toDomain().role() == MessageRole.ASSISTANT)
                        .orElseThrow(ConversationNotFoundException::new);
        message.updateAssistant(
                content,
                status,
                inputTokens,
                outputTokens,
                finishReason,
                providerRequestId,
                updatedAt);
        conversation.touch(updatedAt);
        return messages.saveAndFlush(message).toDomain();
    }

    @Override
    @Transactional
    public ConversationToolCall recordToolCall(
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
            Instant startedAt) {
        lockConversation(conversationId, ownerId);
        messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(ConversationNotFoundException::new);
        ConversationMessageToolCallEntity entity =
                new ConversationMessageToolCallEntity(
                        toolCallId,
                        messageId,
                        generationRound,
                        sequence,
                        toolName,
                        providerToolCallId,
                        arguments,
                        status,
                        startedAt,
                        startedAt);
        return toolCalls.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public ConversationToolCall updateToolCallResult(
            UUID conversationId,
            UUID ownerId,
            UUID toolCallId,
            MessageToolCallStatus status,
            Boolean isError,
            Map<String, Object> result,
            String errorCode,
            Instant completedAt,
            Instant updatedAt) {
        lockConversation(conversationId, ownerId);
        ConversationMessageToolCallEntity entity =
                toolCalls
                        .findById(toolCallId)
                        .filter(candidate -> belongsToConversation(candidate, conversationId))
                        .orElseThrow(ConversationNotFoundException::new);
        entity.updateResult(status, isError, result, errorCode, completedAt, updatedAt);
        return toolCalls.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ConversationToolCall>> findToolCallsForMessages(
            Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return toolCalls
                .findAllByMessageIdInOrderByMessageIdAscGenerationRoundAscSequenceAsc(messageIds)
                .stream()
                .map(ConversationMessageToolCallEntity::toDomain)
                .collect(Collectors.groupingBy(ConversationToolCall::messageId));
    }

    private boolean belongsToConversation(
            ConversationMessageToolCallEntity toolCall, UUID conversationId) {
        return messages.findByIdAndConversationId(toolCall.getMessageId(), conversationId)
                .isPresent();
    }

    @Override
    @Transactional
    public Conversation replaceSelectedDocuments(
            UUID conversationId, UUID ownerId, List<UUID> documentIds, Instant updatedAt) {
        ConversationEntity conversation = lockConversation(conversationId, ownerId);
        jdbcTemplate.update(
                "DELETE FROM chat.conversation_document WHERE conversation_id = ?", conversationId);
        if (!documentIds.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO chat.conversation_document (conversation_id, document_id, created_at)
                    VALUES (?, ?, ?)
                    """,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setObject(1, conversationId);
                            ps.setObject(2, documentIds.get(i));
                            ps.setTimestamp(3, Timestamp.from(updatedAt));
                        }

                        @Override
                        public int getBatchSize() {
                            return documentIds.size();
                        }
                    });
        }
        conversation.touch(updatedAt);
        conversations.flush();
        return conversation.toDomain(List.copyOf(documentIds));
    }

    @Override
    @Transactional
    public void recordMessageDocuments(
            UUID conversationId,
            UUID ownerId,
            UUID messageId,
            List<MessageDocumentEntry> entries,
            Instant createdAt) {
        lockConversation(conversationId, ownerId);
        messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(ConversationNotFoundException::new);
        List<MessageDocumentEntity> entities =
                entries.stream()
                        .map(
                                entry ->
                                        new MessageDocumentEntity(
                                                UUID.randomUUID(),
                                                messageId,
                                                entry.documentId(),
                                                entry.chunkId(),
                                                entry.relation(),
                                                entry.score(),
                                                createdAt))
                        .toList();
        messageDocuments.saveAllAndFlush(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<MessageDocumentRef>> findCitationsForMessages(
            Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageDocuments.findAllByMessageIdInOrderByMessageIdAsc(messageIds).stream()
                .collect(
                        Collectors.groupingBy(
                                MessageDocumentEntity::getMessageId,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        entity ->
                                                new MessageDocumentRef(
                                                        entity.getDocumentId(),
                                                        entity.getChunkId(),
                                                        entity.getRelation(),
                                                        entity.getScore()),
                                        Collectors.toList())));
    }

    private List<UUID> selectedDocumentIds(UUID conversationId) {
        return jdbcTemplate.query(
                """
                SELECT document_id FROM chat.conversation_document
                WHERE conversation_id = ? ORDER BY created_at
                """,
                (rs, rowNum) -> rs.getObject("document_id", UUID.class),
                conversationId);
    }

    private Map<UUID, List<UUID>> selectedDocumentIdsByConversation(
            Collection<UUID> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        UUID[] conversationIdArray = conversationIds.toArray(new UUID[0]);
        return jdbcTemplate.query(
                """
                SELECT conversation_id, document_id FROM chat.conversation_document
                WHERE conversation_id = ANY(?) ORDER BY conversation_id, created_at
                """,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", conversationIdArray)),
                rs -> {
                    Map<UUID, List<UUID>> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        UUID conversationId = rs.getObject("conversation_id", UUID.class);
                        UUID documentId = rs.getObject("document_id", UUID.class);
                        result.computeIfAbsent(conversationId, key -> new ArrayList<>())
                                .add(documentId);
                    }
                    return result;
                });
    }

    private ConversationEntity lockConversation(UUID conversationId, UUID ownerId) {
        return conversations
                .findOwnedForUpdate(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private void rejectActiveGeneration(UUID conversationId) {
        if (messages.existsByConversationIdAndStatus(conversationId, MessageStatus.STREAMING)) {
            throw new ChatConflictException("A generation is already active");
        }
    }

    private ConversationMessage assistantMessage(
            UUID id,
            UUID conversationId,
            long position,
            UUID providerConnectionId,
            ProviderType providerType,
            String modelId,
            UUID regeneratedFromMessageId,
            Instant createdAt) {
        return new ConversationMessage(
                id,
                conversationId,
                position,
                MessageRole.ASSISTANT,
                "",
                providerConnectionId,
                providerType,
                modelId,
                MessageStatus.STREAMING,
                null,
                null,
                null,
                null,
                regeneratedFromMessageId,
                0,
                createdAt,
                createdAt);
    }
}
