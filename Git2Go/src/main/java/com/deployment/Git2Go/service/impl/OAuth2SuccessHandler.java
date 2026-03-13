package com.deployment.Git2Go.service.impl;

import com.deployment.Git2Go.entity.User;
import com.deployment.Git2Go.repository.UserRepository;
import com.deployment.Git2Go.service.impl.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Extract GitHub user info
        String githubId  = String.valueOf(oAuth2User.getAttribute("id"));
        String name      = oAuth2User.getAttribute("name");
        String email     = oAuth2User.getAttribute("email");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");
        String login     = oAuth2User.getAttribute("login");

        // Some GitHub accounts don't expose email publicly — fallback
        if (email == null || email.isBlank()) {
            email = login + "@github.com";
        }

        // ⭐ Get the GitHub OAuth access token
        OAuth2AuthorizedClient client = authorizedClientService
            .loadAuthorizedClient("github", authentication.getName());

        String githubToken = null;
        if (client != null && client.getAccessToken() != null) {
            githubToken = client.getAccessToken().getTokenValue();
        }

        // Find existing user or create new one
        final String finalEmail    = email;
        final String finalGithubToken = githubToken;

        User user = userRepository.findByGithubId(githubId)
            .orElseGet(() -> userRepository.findByEmail(finalEmail)
                .orElse(new User()));

        user.setGithubId(githubId);
        user.setName(name != null ? name : login);
        user.setEmail(finalEmail);
        user.setAvatarUrl(avatarUrl);

        // ⭐ Always update the token (it may have changed)
        if (finalGithubToken != null) {
            user.setGithubToken(finalGithubToken);
        }

        userRepository.save(user);
        log.info("GitHub OAuth login: {} ({})", user.getName(), user.getEmail());

        // Issue JWT and redirect to frontend with token
        String jwt = jwtService.generateToken(user.getEmail());

        // Redirect to your React frontend with token in URL
        // Frontend reads it once, stores in memory, then removes from URL
        response.sendRedirect(appBaseUrl + "/auth/callback?token=" + jwt);
    }
}