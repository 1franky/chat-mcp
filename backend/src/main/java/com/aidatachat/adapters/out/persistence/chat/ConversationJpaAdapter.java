package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.application.exception.ChatConflictException;
import com.aidatachat.application.exception.ConversationNotFoundException;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.domain.model.Conversation;
import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConversationJpaAdapter implements ConversationRepository {

    private final SpringDataConversationRepository conversations;
    private final SpringDataConversationMessageRepository messages;

    public ConversationJpaAdapter(
            SpringDataConversationRepository conversations,
            SpringDataConversationMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
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
        return new ConversationPage(
                result.getContent().stream().map(ConversationEntity::toDomain).toList(),
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
                .map(ConversationEntity::toDomain);
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
        return conversations.saveAndFlush(entity).toDomain();
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
                conversation.toDomain(), saved.get(0).toDomain(), saved.get(1).toDomain());
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
