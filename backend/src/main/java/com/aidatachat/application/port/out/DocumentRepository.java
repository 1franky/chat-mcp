package com.aidatachat.application.port.out;

import java.util.UUID;

public interface DocumentRepository {

    boolean existsByIdAndOwnerId(UUID documentId, UUID ownerId);
}
