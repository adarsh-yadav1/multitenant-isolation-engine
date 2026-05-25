package com.saas.multitenant.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

// AES-256-GCM encryption for tenant DB passwords
// GCM mode provides both encryption AND authentication (tamper detection)
// Each encryption produces a unique IV — same plaintext encrypts differently each time
// Format stored in DB: Base64(IV + ciphertext + auth_tag)

@Slf4j
@Component
public class AesEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96 bits — recommended for GCM
    private static final int GCM_TAG_LENGTH = 128;  // 128 bits auth tag

    private final SecretKey secretKey;

    public AesEncryptionService(
            @Value("${app.encryption.db-key}") String base64Key) {
        // Key must be exactly 32 bytes for AES-256
        byte[] keyBytes = base64Key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "DB encryption key must be at least 32 characters long");
        }
        // Use first 32 bytes
        byte[] key32 = new byte[32];
        System.arraycopy(keyBytes, 0, key32, 0, 32);
        this.secretKey = new SecretKeySpec(key32, "AES");
    }

    // Encrypts plaintext and returns Base64(IV + ciphertext)
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);  // unique IV per encryption

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext so we can extract it during decryption
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    // Decrypts Base64(IV + ciphertext) and returns plaintext
    public String decrypt(String encryptedBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

            // Extract IV from first 12 bytes
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Remaining bytes are ciphertext + auth tag
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    // Returns true if value looks like an encrypted string (Base64 encoded)
    // Used to avoid double-encrypting already encrypted values
    public boolean isEncrypted(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length > GCM_IV_LENGTH;
        } catch (Exception e) {
            return false;
        }
    }
}