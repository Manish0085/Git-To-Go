package com.git2go.platform.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Webhook Secret Generator — unique random secret generate karta hai.
 *
 * SecureRandom use kar rahe hain (not Random) kyunki:
 * - Random predictable hai — seed guess kar sakte hain
 * - SecureRandom cryptographically secure hai — unpredictable
 *
 * Interview: "SecureRandom use kiya hai webhook secrets ke liye.
 * java.util.Random Linear Congruential Generator use karta hai jo
 * predictable hai. SecureRandom OS ke entropy pool se seed leta hai
 * (/dev/urandom on Linux) — cryptographically secure hai."
 */
@Component
public class WebhookSecretGenerator {

    private static final int SECRET_LENGTH_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
