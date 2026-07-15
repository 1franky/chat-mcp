package com.aidatachat.application.port.out;

import java.io.InputStream;
import java.util.UUID;

public interface DocumentStoragePort {

    String store(UUID ownerId, String generatedName, InputStream content);

    InputStream open(UUID ownerId, String storageKey);

    void delete(UUID ownerId, String storageKey);
}
