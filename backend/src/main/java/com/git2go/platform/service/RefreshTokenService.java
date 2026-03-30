package com.git2go.platform.service;

import com.git2go.platform.entity.RefreshToken;
import com.git2go.platform.entity.User;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.repository.RefreshTokenRepository;
import com.git2go.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token Service
 *
 * Flow:
 * 1. Login/Signup pe access token + refresh token dono generate hote hain
 * 2. Access token short-lived (1 hour) — API calls ke liye
 * 3. Refresh token long-lived (7 days) — DB me stored
 * 4. Access token expire → client refresh token bhejta hai → naya access token milta hai
 * 5. Refresh token expire → user ko dobara login karna padega
 * 6. Logout → refresh token delete → revoked
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Naya refresh token generate + DB me save
     */
    @Transactional
    public RefreshToken createRefreshToken(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString()) // random unique string
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Refresh token verify karo — valid hai ya expire ho gaya
     */
    @Transactional
    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        // Expire check
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            // Expire ho gaya — DB se delete karo aur error throw karo
            refreshTokenRepository.delete(refreshToken);
            throw new ApiException("Refresh token expired. Please login again.", HttpStatus.UNAUTHORIZED);
        }

        return refreshToken;
    }

    /**
     * Logout — user ke saare refresh tokens delete
     */
    @Transactional
    public void deleteByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}
