package com.git2go.platform.service;

import com.git2go.platform.dto.response.GitHubRepoResponse;
import com.git2go.platform.entity.User;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.repository.UserRepository;
import com.git2go.platform.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GitHub Service — GitHub API se user ke repositories fetch karta hai.
 *
 * Interview: "External API calls ke liye defensive programming follow ki hai —
 * har call try-catch me hai, specific HTTP errors handle kiye hain (401, 403, etc.),
 * aur null-safe mapping ki hai. WebClient ka onStatus() use kiya hai jo
 * HTTP error codes ko meaningful exceptions me convert karta hai."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;

    private final WebClient gitHubWebClient = WebClient.builder()
            .baseUrl("https://api.github.com")
            .build();

    public List<GitHubRepoResponse> getUserRepositories(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        if (user.getGithubAccessToken() == null) {
            throw new ApiException(
                    "GitHub account not linked. Please login with GitHub first.",
                    HttpStatus.BAD_REQUEST
            );
        }

        // Decrypt token — handle corruption gracefully
        String accessToken;
        try {
            accessToken = encryptionUtil.decrypt(user.getGithubAccessToken());
        } catch (RuntimeException e) {
            log.error("GitHub token corrupted for user {}: {}", userEmail, e.getMessage());
            throw new ApiException("GitHub token corrupted. Please re-login with GitHub.", HttpStatus.BAD_REQUEST);
        }

        try {
            List<Map> repos = gitHubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("per_page", 100)
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    // HTTP error codes ko handle karo — GitHub se specific errors aate hain
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        if (response.statusCode().value() == 401) {
                            // Token expire ya revoke ho gaya
                            return response.createException().map(ex ->
                                    new ApiException("GitHub token expired. Please re-login with GitHub.", HttpStatus.UNAUTHORIZED));
                        }
                        if (response.statusCode().value() == 403) {
                            // Rate limit hit ya permission denied
                            return response.createException().map(ex ->
                                    new ApiException("GitHub API rate limit exceeded or access denied.", HttpStatus.FORBIDDEN));
                        }
                        return response.createException().map(ex ->
                                new ApiException("GitHub API error: " + response.statusCode(), HttpStatus.BAD_REQUEST));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.createException().map(ex ->
                                    new ApiException("GitHub server is currently unavailable. Try again later.", HttpStatus.SERVICE_UNAVAILABLE)))
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();

            if (repos == null || repos.isEmpty()) {
                return Collections.emptyList();
            }

            return repos.stream()
                    .map(this::mapToGitHubRepoResponse)
                    .toList();

        } catch (ApiException e) {
            // Hamari custom exception — rethrow karo
            throw e;
        } catch (WebClientResponseException e) {
            // WebClient ki exception — HTTP error jo onStatus se miss ho gayi
            log.error("GitHub API HTTP error: {} - {}", e.getStatusCode(), e.getMessage());
            throw new ApiException("GitHub API request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            // Network timeout, DNS failure, connection refused, etc.
            log.error("GitHub API call failed: {}", e.getMessage());
            throw new ApiException("Unable to reach GitHub. Please check your connection and try again.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Null-safe mapping — GitHub API me koi bhi field null aa sakta hai
     * Bina null check ke NullPointerException aayegi
     */
    private GitHubRepoResponse mapToGitHubRepoResponse(Map<String, Object> repo) {
        return GitHubRepoResponse.builder()
                .id(repo.get("id") != null ? ((Number) repo.get("id")).longValue() : 0L)
                .name((String) repo.getOrDefault("name", "unknown"))
                .fullName((String) repo.getOrDefault("full_name", ""))
                .description((String) repo.get("description")) // null allowed
                .htmlUrl((String) repo.getOrDefault("html_url", ""))
                .cloneUrl((String) repo.getOrDefault("clone_url", ""))
                .language((String) repo.get("language")) // null allowed — repo me koi code nahi toh null
                .defaultBranch((String) repo.getOrDefault("default_branch", "main"))
                .isPrivate(Boolean.TRUE.equals(repo.get("private")))
                .stargazersCount(repo.get("stargazers_count") != null ? ((Number) repo.get("stargazers_count")).intValue() : 0)
                .forksCount(repo.get("forks_count") != null ? ((Number) repo.get("forks_count")).intValue() : 0)
                .build();
    }
}
