package com.deployment.Git2Go.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookService {

    private final RestTemplate restTemplate;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public boolean registerWebhook(String repoUrl,
                                   String projectId,
                                   String secret,
                                   String githubToken) {
        try {
            // Extract owner/repo from URL
            // e.g. https://github.com/john/my-app → john/my-app
            String path = repoUrl
                .replace("https://github.com/", "")
                .replace(".git", "");

            String[] parts = path.split("/");
            if (parts.length < 2) {
                log.error("Invalid GitHub repo URL: {}", repoUrl);
                return false;
            }

            String owner = parts[0];
            String repo  = parts[1];

            String apiUrl = "https://api.github.com/repos/"
                + owner + "/" + repo + "/hooks";

            Map<String, Object> config = Map.of(
                "url",          appBaseUrl + "/api/webhook/github/" + projectId,
                "content_type", "json",
                "secret",       secret,
                "insecure_ssl", "0"
            );

            Map<String, Object> body = Map.of(
                "name",   "web",
                "active", true,
                "events", new String[]{"push"},
                "config", config
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(githubToken);
            headers.set("Accept", "application/vnd.github+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            HttpEntity<Map<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook registered for {}/{}", owner, repo);
                return true;
            }

            log.error("GitHub API error: {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            log.error("Failed to register webhook: {}", e.getMessage());
            return false;
        }
    }

    public void deleteWebhook(String repoUrl,
                              String githubToken,
                              Long hookId) {
        try {
            String path = repoUrl
                .replace("https://github.com/", "")
                .replace(".git", "");

            String[] parts = path.split("/");
            String owner = parts[0];
            String repo  = parts[1];

            String apiUrl = "https://api.github.com/repos/"
                + owner + "/" + repo + "/hooks/" + hookId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.set("Accept", "application/vnd.github+json");

            restTemplate.exchange(
                apiUrl, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

            log.info("Webhook deleted for {}/{}", owner, repo);

        } catch (Exception e) {
            log.error("Failed to delete webhook: {}", e.getMessage());
        }
    }
}