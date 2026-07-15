package com.aidatachat.adapters.out.security;

import com.aidatachat.application.port.out.CredentialCipherPort;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmCredentialCipherAdapter implements CredentialCipherPort {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec masterKey;
    private final int keyVersion;
    private final SecureRandom secureRandom;

    public AesGcmCredentialCipherAdapter(String base64MasterKey, int keyVersion) {
        this(base64MasterKey, keyVersion, new SecureRandom());
    }

    AesGcmCredentialCipherAdapter(
            String base64MasterKey, int keyVersion, SecureRandom secureRandom) {
        if (base64MasterKey == null || base64MasterKey.isBlank()) {
            throw new IllegalStateException("CREDENTIAL_MASTER_KEY is required");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64MasterKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "CREDENTIAL_MASTER_KEY must be valid base64", exception);
        }
        try {
            if (decoded.length != KEY_BYTES) {
                throw new IllegalStateException(
                        "CREDENTIAL_MASTER_KEY must decode to exactly 32 bytes");
            }
            if (keyVersion < 1) {
                throw new IllegalStateException("CREDENTIAL_KEY_VERSION must be positive");
            }
            this.masterKey = new SecretKeySpec(decoded, "AES");
            this.keyVersion = keyVersion;
            this.secureRandom = secureRandom;
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    @Override
    public EncryptedCredential encrypt(char[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Credential must not be empty");
        }
        byte[] clearBytes = utf8(plaintext);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(keyVersion));
            return new EncryptedCredential(cipher.doFinal(clearBytes), nonce, keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Credential encryption failed", exception);
        } finally {
            Arrays.fill(clearBytes, (byte) 0);
        }
    }

    @Override
    public char[] decrypt(EncryptedCredential encryptedCredential) {
        if (encryptedCredential.keyVersion() != keyVersion) {
            throw new IllegalStateException("Credential key version is not available");
        }
        byte[] clearBytes = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    masterKey,
                    new GCMParameterSpec(TAG_BITS, encryptedCredential.nonce()));
            cipher.updateAAD(aad(encryptedCredential.keyVersion()));
            clearBytes = cipher.doFinal(encryptedCredential.ciphertext());
            CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(clearBytes));
            char[] result = new char[decoded.remaining()];
            decoded.get(result);
            return result;
        } catch (AEADBadTagException exception) {
            throw new IllegalStateException("Credential authentication failed", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Credential decryption failed", exception);
        } finally {
            if (clearBytes != null) {
                Arrays.fill(clearBytes, (byte) 0);
            }
        }
    }

    private byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private byte[] aad(int version) {
        return ("ai-data-chat:provider-credential:v" + version).getBytes(StandardCharsets.US_ASCII);
    }
}
