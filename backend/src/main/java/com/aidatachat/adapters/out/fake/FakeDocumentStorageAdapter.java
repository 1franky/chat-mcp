package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.exception.DocumentStorageException;
import com.aidatachat.application.port.out.DocumentStoragePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeDocumentStorageAdapter implements DocumentStoragePort {

    private final ConcurrentHashMap<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public String store(UUID ownerId, String generatedName, InputStream content) {
        try {
            storage.put(key(ownerId, generatedName), content.readAllBytes());
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to store document", e);
        }
        return generatedName;
    }

    @Override
    public InputStream open(UUID ownerId, String storageKey) {
        byte[] bytes = storage.get(key(ownerId, storageKey));
        if (bytes == null) {
            throw new DocumentStorageException("Document not found: " + storageKey);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(UUID ownerId, String storageKey) {
        storage.remove(key(ownerId, storageKey));
    }

    private String key(UUID ownerId, String storageKey) {
        return ownerId + "/" + storageKey;
    }
}
