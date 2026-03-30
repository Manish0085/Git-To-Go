package com.git2go.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Rate Limit Filter — Brute force aur DDoS basic protection.
 *
 * Sirf /api/auth/** endpoints pe active hai (login, signup).
 * Per IP: max 20 requests per minute.
 *
 * Interview: "Sliding window rate limiting implement ki hai. Har IP ke liye
 * ek queue maintain hota hai with request timestamps. Naya request aane pe
 * 1 minute se purane timestamps hata dete hain aur check karte hain ki
 * queue size limit se zyada toh nahi. ConcurrentHashMap use kiya hai
 * thread-safety ke liye — multiple requests concurrently aa sakti hain."
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final long ONE_MINUTE_MS = 60_000;

    // IP → timestamps of recent requests
    private final Map<String, ConcurrentLinkedQueue<Long>> requestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        long now = System.currentTimeMillis();

        // IP ke liye queue le (ya naya banao)
        ConcurrentLinkedQueue<Long> timestamps = requestCounts.computeIfAbsent(clientIp, k -> new ConcurrentLinkedQueue<>());

        // 1 minute se purane timestamps hatao (sliding window)
        while (!timestamps.isEmpty() && timestamps.peek() < now - ONE_MINUTE_MS) {
            timestamps.poll();
        }

        if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            // Limit exceed — 429 Too Many Requests
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Too many requests. Please try again later.\"}");
            return; // Request aage nahi jaayegi
        }

        // Request count me add karo
        timestamps.add(now);

        filterChain.doFilter(request, response);
    }

    /**
     * Sirf auth endpoints pe rate limiting lagegi — baaki endpoints pe nahi
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/auth/");
    }

    /**
     * Client ka real IP nikalo — proxy/load balancer ke peeche ho toh X-Forwarded-For header check karo
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim(); // First IP = real client
        }
        return request.getRemoteAddr();
    }
}
