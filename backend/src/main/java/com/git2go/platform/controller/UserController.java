package com.git2go.platform.controller;

import com.git2go.platform.dto.response.GitHubRepoResponse;
import com.git2go.platform.dto.response.UserProfileResponse;
import com.git2go.platform.entity.User;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.repository.UserRepository;
import com.git2go.platform.service.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User Controller — Authenticated user ke endpoints.
 *
 * GET /api/user/me           → Current logged-in user ki profile
 * GET /api/user/github/repos → GitHub repos (sirf GitHub se login kiya ho toh)
 *
 * @AuthenticationPrincipal — Spring Security automatically inject karta hai
 * currently authenticated user. Ye JwtAuthenticationFilter ne SecurityContext
 * me set kiya tha — yaad hai? Ab yahan use ho raha hai.
 *
 * Interview: "SecurityContext se current user extract karta hoon using
 * @AuthenticationPrincipal annotation. Ye tightly coupled nahi hai —
 * Spring Security ka standard mechanism hai. Agar authentication
 * mechanism change bhi karu (JWT se OAuth2 session pe), ye annotation
 * waise hi kaam karega."
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final GitHubService gitHubService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .build();

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/github/repos")
    public ResponseEntity<List<GitHubRepoResponse>> getGitHubRepos(@AuthenticationPrincipal UserDetails userDetails) {
        List<GitHubRepoResponse> repos = gitHubService.getUserRepositories(userDetails.getUsername());
        return ResponseEntity.ok(repos);
    }
}
