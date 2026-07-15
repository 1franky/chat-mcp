package com.aidatachat.adapters.out.persistence.chat;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    @Query(
            value =
                    """
                    select conversation from ConversationEntity conversation
                    where conversation.ownerId = :ownerId
                      and (:query = '' or lower(conversation.title) like
                          concat('%', lower(:query), '%'))
                    """,
            countQuery =
                    """
                    select count(conversation) from ConversationEntity conversation
                    where conversation.ownerId = :ownerId
                      and (:query = '' or lower(conversation.title) like
                          concat('%', lower(:query), '%'))
                    """)
    Page<ConversationEntity> findOwned(UUID ownerId, String query, Pageable pageable);

    Optional<ConversationEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select conversation from ConversationEntity conversation
            where conversation.id = :id and conversation.ownerId = :ownerId
            """)
    Optional<ConversationEntity> findOwnedForUpdate(UUID id, UUID ownerId);
}
