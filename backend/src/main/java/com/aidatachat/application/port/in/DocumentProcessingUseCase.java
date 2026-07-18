package com.aidatachat.application.port.in;

import java.util.UUID;

/**
 * Entry point into the extraction/normalization/chunking/embedding pipeline that takes a document
 * from {@code UPLOADED} to {@code READY} (or {@code FAILED}). Not exposed over HTTP — driven
 * internally by {@code DocumentManagementService} after a successful upload, fire-and-forget on a
 * dedicated executor.
 */
public interface DocumentProcessingUseCase {

    void processDocument(UUID ownerId, UUID documentId);
}
