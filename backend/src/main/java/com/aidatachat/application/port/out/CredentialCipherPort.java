package com.aidatachat.application.port.out;

import java.util.Arrays;
import java.util.Objects;

public interface CredentialCipherPort {

    EncryptedCredential encrypt(char[] plaintext);

    char[] decrypt(EncryptedCredential encryptedCredential);

    record EncryptedCredential(byte[] ciphertext, byte[] nonce, int keyVersion) {

        public EncryptedCredential {
            Objects.requireNonNull(ciphertext, "ciphertext is required");
            Objects.requireNonNull(nonce, "nonce is required");
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
}
