package com.git2go.platform.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT Token Provider — Token banata hai, verify karta hai, aur parse karta hai.
 *
 * Flow:
 * 1. User login karta hai → generateToken() se JWT milta hai
 * 2. Har request pe client JWT bhejta hai Authorization header me
 * 3. Server validateToken() se check karta hai ki token valid hai ya nahi
 * 4. getUserEmailFromToken() se user identify hota hai
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long jwtExpirationMs;

    // Constructor Injection — @Value se application.yaml se values aati hain
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long jwtExpirationMs) {
        // Base64 encoded secret se HMAC key generate kar rahe hain
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * JWT Token generate karta hai user email ke basis pe.
     * Token me hota hai: subject (email), issued time, expiry time, signature
     */
    public String generateToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)                    // kon hai — email as identifier
                .issuedAt(now)                     // kab bana
                .expiration(expiryDate)            // kab expire hoga
                .signWith(secretKey)               // secret key se sign — tamper-proof
                .compact();                        // final JWT string
    }

    /**
     * Token se user email extract karta hai.
     * Internally token parse + verify hota hai — agar tampered hai toh exception aayegi.
     */
    public String getUserEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Token valid hai ya nahi — expired toh nahi, tampered toh nahi, format sahi hai ya nahi
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token invalid hai — expired, tampered, malformed, etc.
            return false;
        }
    }
}
