package com.aidatachat.adapters.out.persistence.chat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMessageDocumentRepository extends JpaRepository<MessageDocumentEntity, UUID> {

    List<MessageDocumentEntity> findAllByMessageIdInOrderByMessageIdAsc(
            Collection<UUID> messageIds);
}
