package com.deployment.Git2Go.controller;

import com.deployment.Git2Go.entity.Project;
import com.deployment.Git2Go.repository.ProjectRepository;
import com.deployment.Git2Go.service.impl.DeploymentServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ProjectRepository projectRepository;
    private final DeploymentServiceImpl deploymentService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github/{projectId}")
    public ResponseEntity<String> handlePush(
            @PathVariable String projectId,
            @RequestHeader(value = "X-Hub-Signature-256",
                required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event",
                defaultValue = "push") String event,
            @RequestBody String payload) {

        log.info("Webhook: project={} event={}", projectId, event);

        if ("ping".equals(event)) {
            return ResponseEntity.ok("pong — Git2Go connected!");
        }

        if (!"push".equals(event)) {
            return ResponseEntity.ok("Ignored: " + event);
        }

        Project project = projectRepository.findById(projectId)
            .orElse(null);

        if (project == null) {
            return ResponseEntity.status(404).body("Project not found");
        }

        // Verify GitHub HMAC signature
        if (signature != null && project.getWebhookSecret() != null) {
            if (!verifySignature(payload, signature,
                    project.getWebhookSecret())) {
                log.warn("Invalid webhook signature for project {}",
                    projectId);
                return ResponseEntity.status(401).body("Invalid signature");
            }
        }

        try {
            JsonNode json = objectMapper.readTree(payload);

            String pushedBranch = json.path("ref").asText("")
                .replace("refs/heads/", "");

            String trackedBranch = project.getBranch() != null
                ? project.getBranch() : "main";

            if (!pushedBranch.equals(trackedBranch)) {
                log.info("Ignoring push to '{}', tracking '{}'",
                    pushedBranch, trackedBranch);
                return ResponseEntity.ok("Branch not tracked");
            }

            String commitSha = json.path("after").asText();
            String commitMsg = json.path("head_commit")
                .path("message").asText("webhook deploy");
            String pusher = json.path("pusher")
                .path("name").asText("unknown");

            log.info("Auto-deploy by {} commit={}", pusher, commitSha);

            // ⭐ Trigger auto deployment
            deploymentService.triggerFromWebhook(
                project, commitSha, commitMsg);

            return ResponseEntity.ok(
                "Auto-deploy triggered — commit: " + commitSha);

        } catch (Exception e) {
            log.error("Webhook error", e);
            return ResponseEntity.status(500)
                .body("Error: " + e.getMessage());
        }
    }

    private boolean verifySignature(String payload,
                                     String signature,
                                     String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(
                payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("Signature error", e);
            return false;
        }
    }
}