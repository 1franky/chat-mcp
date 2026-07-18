package com.aidatachat.application.service;

import com.aidatachat.application.exception.DocumentNotFoundException;
import com.aidatachat.application.exception.DocumentStorageException;
import com.aidatachat.application.exception.DocumentTooLargeException;
import com.aidatachat.application.exception.UnsupportedDocumentTypeException;
import com.aidatachat.application.port.in.DocumentManagementUseCase;
import com.aidatachat.application.port.out.AuditRepository;
import com.aidatachat.application.port.out.DocumentMimeDetectionPort;
import com.aidatachat.application.port.out.DocumentRepository;
import com.aidatachat.application.port.out.DocumentRepository.DocumentPage;
import com.aidatachat.application.port.out.DocumentStoragePort;
import com.aidatachat.domain.model.Document;
import com.aidatachat.domain.model.DocumentStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

public final class DocumentManagementService implements DocumentManagementUseCase {

    private static final Map<String, Set<String>> ALLOWED_MIME_BY_EXTENSION =
            Map.of(
                    "pdf", Set.of("application/pdf"),
                    "docx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument"
                                            + ".wordprocessingml.document"),
                    "txt", Set.of("text/plain"),
                    "md", Set.of("text/plain", "text/markdown"),
                    "markdown", Set.of("text/plain", "text/markdown"),
                    "csv", Set.of("text/plain", "text/csv"),
                    "json", Set.of("text/plain", "application/json"));

    private static final int MAX_ZIP_ENTRIES = 2_000;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;

    private final DocumentRepository documents;
    private final DocumentStoragePort storage;
    private final DocumentMimeDetectionPort mimeDetection;
    private final AuditRepository audit;
    private final Clock clock;
    private final long maxUploadBytes;

    public DocumentManagementService(
            DocumentRepository documents,
            DocumentStoragePort storage,
            DocumentMimeDetectionPort mimeDetection,
            AuditRepository audit,
            Clock clock,
            long maxUploadBytes) {
        this.documents = Objects.requireNonNull(documents);
        this.storage = Objects.requireNonNull(storage);
        this.mimeDetection = Objects.requireNonNull(mimeDetection);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.maxUploadBytes = maxUploadBytes;
    }

    @Override
    public DocumentListView listDocuments(
            UUID ownerId, DocumentStatus statusFilter, int page, int size) {
        UUID validOwner = requireOwner(ownerId);
        DocumentPage result = documents.findAllByOwnerId(validOwner, statusFilter, page, size);
        return new DocumentListView(
                result.items().stream().map(this::toView).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @Override
    public DocumentView getDocument(UUID ownerId, UUID documentId) {
        return toView(findOwned(ownerId, documentId));
    }

    @Override
    public UploadDocumentResult uploadDocument(UUID ownerId, UploadDocumentCommand command) {
        UUID validOwner = requireOwner(ownerId);
        if (command.declaredSize() <= 0) {
            throw new IllegalArgumentException("file is required");
        }
        if (command.declaredSize() > maxUploadBytes) {
            throw new DocumentTooLargeException();
        }
        byte[] content = readBoundedBytes(command.content());
        if (content.length == 0) {
            throw new IllegalArgumentException("file is required");
        }

        String sanitizedFilename = sanitizeFilename(command.originalFilename());
        String extension = extractExtension(sanitizedFilename);
        Set<String> allowedMimeTypes = ALLOWED_MIME_BY_EXTENSION.get(extension);
        if (allowedMimeTypes == null) {
            throw new UnsupportedDocumentTypeException();
        }
        String detectedMimeType = mimeDetection.detect(content, sanitizedFilename);
        if (!allowedMimeTypes.contains(detectedMimeType)) {
            throw new UnsupportedDocumentTypeException();
        }
        if ("docx".equals(extension)) {
            guardAgainstZipBomb(content);
        }
        String contentHash = sha256Hex(content);

        Optional<Document> existing =
                documents.findByOwnerIdAndContentHash(validOwner, contentHash);
        if (existing.isPresent()) {
            return new UploadDocumentResult(toView(existing.get()), false);
        }

        Instant now = clock.instant();
        String generatedName = UUID.randomUUID().toString();
        String storageKey =
                storage.store(validOwner, generatedName, new ByteArrayInputStream(content));
        Document document =
                new Document(
                        UUID.randomUUID(),
                        validOwner,
                        sanitizedFilename,
                        storageKey,
                        detectedMimeType,
                        content.length,
                        contentHash,
                        DocumentStatus.UPLOADED,
                        null,
                        null,
                        null,
                        0,
                        0,
                        now,
                        now);
        Document saved = documents.save(document);
        appendAudit(
                validOwner,
                saved.id(),
                "DOCUMENT_UPLOADED",
                command.remoteAddress(),
                Map.of("mimeType", saved.mimeType(), "byteSize", String.valueOf(saved.byteSize())));
        return new UploadDocumentResult(toView(saved), true);
    }

    @Override
    public void deleteDocument(UUID ownerId, UUID documentId, String remoteAddress) {
        UUID validOwner = requireOwner(ownerId);
        Document document = findOwned(validOwner, documentId);
        storage.delete(validOwner, document.storageKey());
        documents.deleteByIdAndOwnerId(documentId, validOwner);
        appendAudit(validOwner, documentId, "DOCUMENT_DELETED", remoteAddress, Map.of());
    }

    private Document findOwned(UUID ownerId, UUID documentId) {
        return documents
                .findByIdAndOwnerId(documentId, requireOwner(ownerId))
                .orElseThrow(DocumentNotFoundException::new);
    }

    private UUID requireOwner(UUID ownerId) {
        return Objects.requireNonNull(ownerId, "ownerId is required");
    }

    private byte[] readBoundedBytes(InputStream content) {
        try {
            int limit = Math.toIntExact(maxUploadBytes);
            byte[] bytes = content.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new DocumentTooLargeException();
            }
            return bytes;
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to read uploaded file", e);
        }
    }

    private String sanitizeFilename(String rawName) {
        String base = rawName == null ? "" : rawName;
        String sanitized = base.replaceAll("[^A-Za-z0-9 ._-]", "_").trim();
        if (sanitized.isBlank()) {
            sanitized = "documento";
        }
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }

    private String extractExtension(String sanitizedFilename) {
        int dot = sanitizedFilename.lastIndexOf('.');
        if (dot < 0 || dot == sanitizedFilename.length() - 1) {
            return "";
        }
        return sanitizedFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void guardAgainstZipBomb(byte[] content) {
        int entryCount = 0;
        long totalUncompressed = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            while (zip.getNextEntry() != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new DocumentTooLargeException();
                }
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    totalUncompressed += read;
                    if (totalUncompressed > MAX_ZIP_UNCOMPRESSED_BYTES) {
                        throw new DocumentTooLargeException();
                    }
                }
            }
        } catch (IOException e) {
            throw new UnsupportedDocumentTypeException();
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void appendAudit(
            UUID actorId,
            UUID targetId,
            String eventType,
            String remoteAddress,
            Map<String, String> metadata) {
        audit.append(
                new AuditRepository.AuditEvent(
                        actorId,
                        targetId,
                        eventType,
                        true,
                        clock.instant(),
                        remoteAddress,
                        metadata));
    }

    private DocumentView toView(Document document) {
        return new DocumentView(
                document.id(),
                document.originalFilename(),
                document.mimeType(),
                document.byteSize(),
                document.status(),
                document.failureReason(),
                document.chunkCount(),
                document.createdAt(),
                document.updatedAt());
    }
}
