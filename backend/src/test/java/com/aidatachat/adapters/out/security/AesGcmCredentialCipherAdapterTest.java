package com.aidatachat.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.port.out.CredentialCipherPort.EncryptedCredential;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AesGcmCredentialCipherAdapterTest {

    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final AesGcmCredentialCipherAdapter cipher =
            new AesGcmCredentialCipherAdapter(TEST_KEY, 1);

    @Test
    void encryptsWithUniqueNoncesAndAuthenticatedDecryption() {
        char[] plaintext = "secret-provider-key".toCharArray();

        EncryptedCredential first = cipher.encrypt(plaintext);
        EncryptedCredential second = cipher.encrypt(plaintext);

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
        assertThat(first.keyVersion()).isOne();
        assertThat(cipher.decrypt(first)).containsExactly(plaintext);
    }

    @Test
    void rejectsTamperedCiphertextAndUnknownKeyVersion() {
        EncryptedCredential encrypted = cipher.encrypt("secret-provider-key".toCharArray());
        byte[] tampered = encrypted.ciphertext();
        tampered[0] ^= 1;

        assertThatThrownBy(
                        () ->
                                cipher.decrypt(
                                        new EncryptedCredential(
                                                tampered,
                                                encrypted.nonce(),
                                                encrypted.keyVersion())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Credential authentication failed");
        assertThatThrownBy(
                        () ->
                                cipher.decrypt(
                                        new EncryptedCredential(
                                                encrypted.ciphertext(), encrypted.nonce(), 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Credential key version is not available");
    }

    @Test
    void encryptedValueDefensivelyCopiesMutableArrays() {
        EncryptedCredential encrypted = cipher.encrypt("secret-provider-key".toCharArray());
        byte[] ciphertext = encrypted.ciphertext();
        byte original = ciphertext[0];
        ciphertext[0] ^= 1;

        assertThat(encrypted.ciphertext()[0]).isEqualTo(original);
        Arrays.fill(ciphertext, (byte) 0);
    }
}
