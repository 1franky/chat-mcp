package com.aidatachat.application.port.out;

import java.util.UUID;

public interface ConversationRepository {

    boolean existsByIdAndOwnerId(UUID conversationId, UUID ownerId);
}
