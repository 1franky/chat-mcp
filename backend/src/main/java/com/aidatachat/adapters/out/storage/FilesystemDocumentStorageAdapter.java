package com.aidatachat.adapters.out.storage;

import com.aidatachat.application.exception.DocumentStorageException;
import com.aidatachat.application.port.out.DocumentStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class FilesystemDocumentStorageAdapter implements DocumentStoragePort {

    private final Path basePath;

    public FilesystemDocumentStorageAdapter(String basePath) {
        this.basePath = Path.of(basePath).normalize().toAbsolutePath();
    }

    @Override
    public String store(UUID ownerId, String generatedName, InputStream content) {
        Path target = resolve(ownerId, generatedName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to store document", e);
        }
        return target.getFileName().toString();
    }

    @Override
    public InputStream open(UUID ownerId, String storageKey) {
        try {
            return Files.newInputStream(resolve(ownerId, storageKey));
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to open document", e);
        }
    }

    @Override
    public void delete(UUID ownerId, String storageKey) {
        try {
            Files.deleteIfExists(resolve(ownerId, storageKey));
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to delete document", e);
        }
    }

    private Path resolve(UUID ownerId, String key) {
        if (key == null || key.isBlank()) {
            throw new DocumentStorageException("Storage key must not be blank");
        }
        String fileName = Path.of(key).getFileName().toString();
        Path ownerDir = basePath.resolve(ownerId.toString()).normalize();
        Path resolved = ownerDir.resolve(fileName).normalize();
        if (!resolved.startsWith(ownerDir)) {
            throw new DocumentStorageException("Storage key escapes owner directory: " + key);
        }
        return resolved;
    }
}
