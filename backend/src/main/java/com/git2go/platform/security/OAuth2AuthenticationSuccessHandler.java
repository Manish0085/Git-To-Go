package com.git2go.platform.security;

import com.git2go.platform.entity.User;
import com.git2go.platform.enums.AuthProvider;
import com.git2go.platform.enums.Role;
import com.git2go.platform.repository.UserRepository;
import com.git2go.platform.util.EncryptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * OAuth2 Login SUCCESS hone ke baad ye handler execute hota hai.
 *
 * Flow:
 * 1. User Google/GitHub pe authorize karta hai
 * 2. Spring OAuth2 user info fetch karta hai
 * 3. YE HANDLER call hota hai
 * 4. Hum user ko DB me save/update karte hain
 * 5. JWT generate karte hain
 * 6. Frontend pe redirect karte hain with token
 *
 * Interview: "OAuth2AuthenticationSuccessHandler implement kiya hai jo
 * SimpleUrlAuthenticationSuccessHandler extend karta hai. OAuth2 success ke baad
 * user info extract karke DB me persist karta hoon — agar user pehli baar aa raha
 * hai toh create, warna update. Phir JWT generate karke frontend pe redirect
 * karta hoon token as query parameter me. Production me token cookie me ya
 * short-lived code exchange pattern use karna chahiye."
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final EncryptionUtil encryptionUtil;
    private final com.git2go.platform.service.RefreshTokenService refreshTokenService;

    @Value("${app.oauth2.redirect-uri}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "google" ya "github"

        // Provider detect karo
        AuthProvider provider = registrationId.equalsIgnoreCase("google")
                ? AuthProvider.GOOGLE
                : AuthProvider.GITHUB;

        // OAuth2 user info extract karo (Google aur GitHub alag format me data bhejte hain)
        String email = extractEmail(oAuth2User, provider);
        String name = extractName(oAuth2User, provider);
        String avatarUrl = extractAvatarUrl(oAuth2User, provider);
        String providerId = extractProviderId(oAuth2User, provider);

        // GitHub ka access token nikalo — repos fetch karne ke liye chahiye baad me
        // final variable — lambda me use hoga
        final String githubAccessToken;
        if (provider == AuthProvider.GITHUB) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    registrationId, oauthToken.getName());
            if (client != null) {
                githubAccessToken = encryptionUtil.encrypt(client.getAccessToken().getTokenValue());
            } else {
                githubAccessToken = null;
            }
        } else {
            githubAccessToken = null;
        }

        // DB me user find karo ya naya create karo
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    // User already exists — update karo (name, avatar change hua ho toh)
                    existingUser.setName(name);
                    existingUser.setAvatarUrl(avatarUrl);
                    if (githubAccessToken != null) {
                        existingUser.setGithubAccessToken(githubAccessToken);
                    }
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // New user — create karo
                    User newUser = User.builder()
                            .name(name)
                            .email(email)
                            .avatarUrl(avatarUrl)
                            .role(Role.USER)
                            .authProvider(provider)
                            .providerId(providerId)
                            .githubAccessToken(githubAccessToken)
                            .emailVerified(true) // OAuth2 users are pre-verified by provider
                            .build();
                    return userRepository.save(newUser);
                });

        // JWT + Refresh Token generate karo
        String jwt = jwtTokenProvider.generateToken(user.getEmail());
        com.git2go.platform.entity.RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

        // Frontend pe redirect karo with BOTH tokens
        String targetUrl = frontendRedirectUri + "?token=" + jwt + "&refreshToken=" + refreshToken.getToken();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    // --- Helper methods: Google aur GitHub alag format me data bhejte hain ---

    private String extractEmail(OAuth2User oAuth2User, AuthProvider provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        if (provider == AuthProvider.GOOGLE) {
            return (String) attributes.get("email");
        } else {
            // GitHub me email null aa sakta hai agar user ne private rakha hai
            String email = (String) attributes.get("email");
            if (email == null) {
                // Fallback: GitHub username se ek placeholder email banao
                email = attributes.get("login") + "@github.com";
            }
            return email;
        }
    }

    private String extractName(OAuth2User oAuth2User, AuthProvider provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        if (provider == AuthProvider.GOOGLE) {
            return (String) attributes.get("name");
        } else {
            String name = (String) attributes.get("name");
            return name != null ? name : (String) attributes.get("login");
        }
    }

    private String extractAvatarUrl(OAuth2User oAuth2User, AuthProvider provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        if (provider == AuthProvider.GOOGLE) {
            return (String) attributes.get("picture");
        } else {
            return (String) attributes.get("avatar_url");
        }
    }

    private String extractProviderId(OAuth2User oAuth2User, AuthProvider provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        if (provider == AuthProvider.GOOGLE) {
            return (String) attributes.get("sub");
        } else {
            return String.valueOf(attributes.get("id"));
        }
    }
}
