package com.aidatachat.application.port.out;

public interface CredentialCipherPort {

    EncryptedCredential encrypt(char[] plaintext);

    char[] decrypt(EncryptedCredential encryptedCredential);

    record EncryptedCredential(byte[] ciphertext, byte[] nonce, int keyVersion) {}
}
