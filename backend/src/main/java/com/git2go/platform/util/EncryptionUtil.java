package com.git2go.platform.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM Encryption Utility — sensitive data encrypt/decrypt karta hai.
 *
 * AES-GCM kyu? AES-CBC ke upar:
 * - GCM authenticated encryption hai — encrypt bhi karta hai, tamper-detect bhi karta hai
 * - CBC me padding oracle attack possible hai, GCM me nahi
 *
 * Interview: "AES-256-GCM use kiya hai sensitive data encryption ke liye.
 * GCM mode authenticated encryption provide karta hai — confidentiality aur
 * integrity dono. Har encryption me unique IV (Initialization Vector) generate
 * hota hai — same plaintext bhi har baar different ciphertext deta hai.
 * Encryption key environment variable se aati hai, code me hardcoded nahi hai."
 */
@Slf4j
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12;        // bytes — GCM standard

    private final SecretKeySpec secretKey;

    public EncryptionUtil(@Value("${app.encryption.key}") String encryptionKey) {
        // Key must be exactly 32 bytes for AES-256
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt — plaintext → Base64 encoded ciphertext
     * IV (random) ko ciphertext ke saath prefix kar dete hain — decrypt me chahiye
     */
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            // Random IV generate — har encryption pe unique hona chahiye
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes());

            // IV + encrypted data combine karo → Base64 encode
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt data");
        }
    }

    /**
     * Decrypt — Base64 ciphertext → plaintext
     * Pehle IV extract karo (first 12 bytes), phir decrypt
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            // First 12 bytes = IV, baaki = encrypted data
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt data");
        }
    }
}
