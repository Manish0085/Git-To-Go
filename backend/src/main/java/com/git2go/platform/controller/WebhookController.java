package com.git2go.platform.controller;

import com.git2go.platform.dto.response.WebhookConfigResponse;
import com.git2go.platform.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Webhook Controller
 *
 * 2 types of endpoints:
 *
 * 1. PUBLIC (GitHub calls these — no JWT):
 *    POST /api/webhooks/github/{projectId}  → Receive webhook event
 *
 * 2. PROTECTED (User manages auto-deploy — JWT required):
 *    POST /api/projects/{id}/webhook/enable   → Enable auto-deploy
 *    POST /api/projects/{id}/webhook/disable  → Disable auto-deploy
 *    GET  /api/projects/{id}/webhook          → Get webhook config (URL + secret)
 *    POST /api/projects/{id}/webhook/regenerate → Regenerate webhook secret
 *
 * Interview: "Webhook endpoint public hai kyunki GitHub ko call karna hai —
 * JWT nahi hoga GitHub ke paas. But HMAC signature se secured hai.
 * Management endpoints JWT protected hain — sirf project owner manage kar sakta hai."
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    // ==================== PUBLIC — GitHub calls this ====================

    /**
     * GitHub webhook receiver.
     *
     * @RequestBody as String — raw payload chahiye signature verification ke liye.
     * Agar Jackson auto-parse kare toh original payload lose ho jaata hai
     * aur signature match nahi karega.
     *
     * ALWAYS returns 200 OK to GitHub — even on errors.
     * GitHub non-200 pe retry karta hai — unnecessary load se bachna hai.
     */
    @PostMapping("/api/webhooks/github/{projectId}")
    public ResponseEntity<Void> receiveGitHubWebhook(
            @PathVariable UUID projectId,
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType) {

        log.info("Webhook received: projectId={}, event={}", projectId, eventType);

        if (signature == null) {
            log.warn("Webhook rejected: missing X-Hub-Signature-256 header for project {}", projectId);
            return ResponseEntity.ok().build(); // Still 200 — don't reveal info to attacker
        }

        if (eventType == null) {
            log.warn("Webhook rejected: missing X-GitHub-Event header for project {}", projectId);
            return ResponseEntity.ok().build();
        }

        try {
            webhookService.processWebhookEvent(projectId, payload, signature, eventType);
        } catch (Exception e) {
            // Log but DON'T return error — GitHub ko 200 hi dena hai
            log.error("Webhook processing failed for project {}: {}", projectId, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    // ==================== PROTECTED — User manages auto-deploy ====================

    @PostMapping("/api/projects/{projectId}/webhook/enable")
    public ResponseEntity<WebhookConfigResponse> enableAutoDeploy(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        WebhookConfigResponse response = webhookService.enableAutoDeploy(projectId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/projects/{projectId}/webhook/disable")
    public ResponseEntity<WebhookConfigResponse> disableAutoDeploy(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        WebhookConfigResponse response = webhookService.disableAutoDeploy(projectId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/projects/{projectId}/webhook")
    public ResponseEntity<WebhookConfigResponse> getWebhookConfig(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        WebhookConfigResponse response = webhookService.getWebhookConfig(projectId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/projects/{projectId}/webhook/regenerate")
    public ResponseEntity<WebhookConfigResponse> regenerateSecret(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        WebhookConfigResponse response = webhookService.regenerateSecret(projectId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
