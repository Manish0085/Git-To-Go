package com.git2go.platform.service;

import com.git2go.platform.entity.User;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Email Verification Service
 *
 * Flow:
 * 1. User signup → verification token generate → email bhejo with link
 * 2. User link click kare → token verify → emailVerified = true
 * 3. Login pe check — agar emailVerified false hai toh login nahi hoga
 * 4. Token 24 hours me expire — resend option available
 *
 * Interview: "Email verification implement ki hai secure token based approach se.
 * UUID token generate hota hai with 24-hour expiry. DB me store hota hai —
 * stateless JWT token nahi use kiya verification ke liye kyunki ye one-time use hai
 * aur revocable hona chahiye. Token use hone ke baad null set karta hoon — replay
 * attack prevent. Resend pe naya token generate hota hai purana invalidate hokar."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.email.verification-url:http://localhost:8080/api/auth/verify-email}")
    private String verificationBaseUrl;

    private static final int TOKEN_EXPIRY_HOURS = 24;

    /**
     * Verification token generate + email bhejo
     */
    @Transactional
    public void sendVerificationEmail(User user) {
        String token = UUID.randomUUID().toString();

        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        userRepository.save(user);

        String verificationUrl = verificationBaseUrl + "?token=" + token;

        emailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationUrl);
        log.info("Verification email sent to: {}", user.getEmail());
    }

    /**
     * Token verify karo — user click kare link pe toh ye chalega
     */
    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException("Verification token is required", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ApiException("Invalid verification token", HttpStatus.BAD_REQUEST));

        // Expiry check
        if (user.getVerificationTokenExpiry() != null
                && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ApiException("Verification token expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }

        // Verify!
        user.setEmailVerified(true);
        user.setVerificationToken(null);        // Token one-time use — null set karo (replay attack prevent)
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        // Welcome email — ab verified hai
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

        log.info("Email verified for user: {}", user.getEmail());
    }

    /**
     * Resend verification email — agar user ne pehli email miss kar di
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new ApiException("Email is already verified", HttpStatus.BAD_REQUEST);
        }

        // Naya token generate — purana automatically invalidate
        sendVerificationEmail(user);
    }
}
