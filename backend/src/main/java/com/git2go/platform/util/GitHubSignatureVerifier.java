package com.git2go.platform.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * GitHub Webhook Signature Verifier
 *
 * GitHub har webhook request ke saath X-Hub-Signature-256 header bhejta hai.
 * Ye header payload ka HMAC-SHA256 hash hai (shared secret se signed).
 *
 * Hum same secret se independently hash generate karte hain aur compare karte hain.
 * Match = genuine GitHub request. No match = someone is trying to spoof.
 *
 * IMPORTANT: MessageDigest.isEqual() use karte hain comparison ke liye —
 * ye timing-safe hai. Agar == operator ya .equals() use karein toh
 * timing attack possible hai (attacker response time se guess kar sakta hai
 * kitne characters match hue).
 *
 * Interview: "HMAC-SHA256 based signature verification implement ki hai.
 * GitHub shared secret se payload sign karta hai, mera server independently
 * verify karta hai. Timing-safe comparison use ki hai MessageDigest.isEqual() se
 * taaki side-channel timing attacks se bacha ja sake."
 */
@Slf4j
@Component
public class GitHubSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * Verify GitHub webhook signature
     *
     * @param payload     — raw request body (JSON string)
     * @param signatureHeader — X-Hub-Signature-256 header value (e.g., "sha256=abc123...")
     * @param secret      — stored webhook secret for this project
     * @return true if signature is valid
     */
    public boolean verifySignature(String payload, String signatureHeader, String secret) {
        if (payload == null || signatureHeader == null || secret == null) {
            log.warn("Webhook verification failed: null parameters");
            return false;
        }

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Webhook verification failed: invalid signature format");
            return false;
        }

        try {
            // GitHub ka signature (hex string, "sha256=" prefix hatake)
            String githubSignature = signatureHeader.substring(SIGNATURE_PREFIX.length());

            // Humara independently generated signature
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = bytesToHex(hash);

            // Timing-safe comparison — constant time me compare hota hai
            // chahe 0 characters match hon ya saare
            boolean valid = MessageDigest.isEqual(
                    githubSignature.getBytes(StandardCharsets.UTF_8),
                    computedSignature.getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                log.warn("Webhook signature mismatch — possible spoofing attempt");
            }

            return valid;

        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
