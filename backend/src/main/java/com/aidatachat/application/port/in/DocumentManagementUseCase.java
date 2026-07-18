package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.DocumentStatus;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentManagementUseCase {

    DocumentListView listDocuments(UUID ownerId, DocumentStatus statusFilter, int page, int size);

    DocumentView getDocument(UUID ownerId, UUID documentId);

    UploadDocumentResult uploadDocument(UUID ownerId, UploadDocumentCommand command);

    void deleteDocument(UUID ownerId, UUID documentId, String remoteAddress);

    record UploadDocumentCommand(
            String originalFilename,
            long declaredSize,
            InputStream content,
            String remoteAddress) {}

    record UploadDocumentResult(DocumentView document, boolean created) {}

    record DocumentView(
            UUID id,
            String originalFilename,
            String mimeType,
            long byteSize,
            DocumentStatus status,
            String failureReason,
            int chunkCount,
            Instant createdAt,
            Instant updatedAt) {}

    record DocumentListView(
            List<DocumentView> items, int page, int size, long totalElements, int totalPages) {}
}
