package com.aidatachat.domain.model;

import java.util.Arrays;
import java.util.Objects;

public record EncryptedProviderCredential(
        byte[] ciphertext, byte[] nonce, int keyVersion, String maskedValue) {

    public EncryptedProviderCredential {
        Objects.requireNonNull(ciphertext, "ciphertext is required");
        Objects.requireNonNull(nonce, "nonce is required");
        Objects.requireNonNull(maskedValue, "maskedValue is required");
        ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        nonce = Arrays.copyOf(nonce, nonce.length);
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
    }

    @Override
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }
}
