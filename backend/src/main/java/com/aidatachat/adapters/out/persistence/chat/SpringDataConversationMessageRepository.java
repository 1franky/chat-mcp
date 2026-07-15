package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.domain.model.MessageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataConversationMessageRepository
        extends JpaRepository<ConversationMessageEntity, UUID> {

    List<ConversationMessageEntity> findAllByConversationIdOrderByPositionAscIdAsc(
            UUID conversationId);

    Optional<ConversationMessageEntity> findByIdAndConversationId(UUID id, UUID conversationId);

    boolean existsByConversationIdAndStatus(UUID conversationId, MessageStatus status);

    @Query(
            """
            select coalesce(max(message.position), 0) from ConversationMessageEntity message
            where message.conversationId = :conversationId
            """)
    long maxPosition(UUID conversationId);
}
