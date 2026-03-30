package com.git2go.platform.config;

import com.git2go.platform.security.JwtAuthenticationFilter;
import com.git2go.platform.security.OAuth2AuthenticationSuccessHandler;
import com.git2go.platform.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security Configuration — Pura security setup yahan hai.
 *
 * Interview: "SecurityFilterChain bean define kiya hai with stateless session policy.
 * CSRF disabled hai kyunki JWT based auth hai — token Authorization header me jaata hai,
 * cookie me nahi, toh CSRF attack possible nahi hai. Custom JWT filter register kiya hai
 * UsernamePasswordAuthenticationFilter se pehle."
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disable — REST API + JWT = no cookies = no CSRF risk
                .csrf(csrf -> csrf.disable())

                // CORS enable — frontend alag port/domain pe hoga toh cross-origin allow karna padega
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Session management — STATELESS kyunki JWT use kar rahe hain
                // Server koi session store nahi karega, har request me token aayega
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // URL authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Logout requires JWT — isliye pehle match karo (order matters)
                        .requestMatchers("/api/auth/logout").authenticated()
                        // Ye endpoints PUBLIC hain — bina login ke access kar sakte hain
                        .requestMatchers(
                                "/api/auth/**",          // signup, login, refresh
                                "/api/webhooks/**",      // GitHub webhook receiver (secured by HMAC signature)
                                "/ws/**",                // WebSocket endpoint
                                "/actuator/**",          // Health + Metrics (Prometheus scrapes this)
                                "/oauth2/**",            // OAuth2 flow URLs
                                "/login/oauth2/**"       // OAuth2 callback URLs
                        ).permitAll()
                        // Baaki sab PROTECTED — JWT token chahiye
                        .anyRequest().authenticated()
                )

                // OAuth2 Login configuration
                .oauth2Login(oauth2 -> oauth2
                        // OAuth2 success hone pe ye handler chalega — JWT generate karega
                        .successHandler(oAuth2SuccessHandler)
                )

                // Filter order: RateLimit → JWT → UsernamePassword
                // Pehle rate limit check, phir JWT verify
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password Encoder — BCrypt use kar rahe hain.
     *
     * Interview: "BCrypt use kiya hai kyunki ye adaptive hashing algorithm hai —
     * internally salt generate karta hai (rainbow table attack prevent), aur
     * cost factor adjustable hai (future me compute power badhne pe strength
     * badha sakte hain). SHA-256 ya MD5 password hashing ke liye suitable nahi
     * hain kyunki wo fast hain — attacker brute force kar sakta hai."
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager — Login ke time email + password verify karne ke liye
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS Configuration — Cross-Origin Resource Sharing
     *
     * Frontend (React — localhost:3000) se backend (localhost:8080) pe request jaayegi.
     * Browser by default cross-origin block karta hai — CORS se allow karte hain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173")); // React dev servers
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
