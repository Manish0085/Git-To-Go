package com.git2go.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.git2go.platform.audit.Auditable;
import com.git2go.platform.dto.response.WebhookConfigResponse;
import com.git2go.platform.entity.Project;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.repository.ProjectRepository;
import com.git2go.platform.util.EncryptionUtil;
import com.git2go.platform.util.GitHubSignatureVerifier;
import com.git2go.platform.util.WebhookSecretGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Webhook Service — GitHub webhook events handle karta hai.
 *
 * Responsibilities:
 * 1. Auto-deploy enable/disable karna
 * 2. Webhook secret generate karna
 * 3. GitHub webhook payload verify + process karna
 * 4. Correct branch pe push hai toh build trigger karna
 *
 * Interview: "Webhook service GitHub push events handle karta hai. Pehle
 * HMAC-SHA256 signature verify karta hai (timing-safe comparison se).
 * Phir payload parse karke branch check karta hai — sirf configured branch
 * pe push hone pe deploy trigger hota hai. Idempotency bhi handle ki hai —
 * agar already building hai toh duplicate deploy nahi hoga."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final ProjectRepository projectRepository;
    private final BuildService buildService;
    private final GitHubSignatureVerifier signatureVerifier;
    private final WebhookSecretGenerator secretGenerator;
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.webhook.base-url:http://localhost:8080}")
    private String webhookBaseUrl;

    // ==================== AUTO DEPLOY MANAGEMENT ====================

    /**
     * Auto-deploy enable karo — webhook secret generate + save
     */
    @Transactional
    @Auditable(action = "ENABLE_AUTO_DEPLOY")
    public WebhookConfigResponse enableAutoDeploy(UUID projectId, String userEmail) {
        Project project = getProjectAndValidate(projectId, userEmail);

        // Secret generate + encrypt karke store karo
        if (project.getWebhookSecret() == null) {
            String plainSecret = secretGenerator.generateSecret();
            project.setWebhookSecret(encryptionUtil.encrypt(plainSecret));
        }
        project.setAutoDeployEnabled(true);
        projectRepository.save(project);

        log.info("Auto-deploy enabled for project: {}", project.getName());
        return buildWebhookConfig(project);
    }

    /**
     * Auto-deploy disable karo
     */
    @Transactional
    @Auditable(action = "DISABLE_AUTO_DEPLOY")
    public WebhookConfigResponse disableAutoDeploy(UUID projectId, String userEmail) {
        Project project = getProjectAndValidate(projectId, userEmail);

        project.setAutoDeployEnabled(false);
        projectRepository.save(project);

        log.info("Auto-deploy disabled for project: {}", project.getName());
        return buildWebhookConfig(project);
    }

    /**
     * Webhook config fetch karo — user ko dikhane ke liye (URL + secret)
     */
    @Transactional(readOnly = true)
    public WebhookConfigResponse getWebhookConfig(UUID projectId, String userEmail) {
        Project project = getProjectAndValidate(projectId, userEmail);
        return buildWebhookConfig(project);
    }

    /**
     * Webhook secret regenerate karo — agar leak ho gaya ho
     */
    @Transactional
    public WebhookConfigResponse regenerateSecret(UUID projectId, String userEmail) {
        Project project = getProjectAndValidate(projectId, userEmail);

        String plainSecret = secretGenerator.generateSecret();
        project.setWebhookSecret(encryptionUtil.encrypt(plainSecret));
        projectRepository.save(project);

        log.info("Webhook secret regenerated for project: {}", project.getName());
        return buildWebhookConfig(project);
    }

    // ==================== WEBHOOK EVENT PROCESSING ====================

    /**
     * GitHub webhook event process karo.
     *
     * Steps:
     * 1. Project find karo
     * 2. Auto-deploy enabled hai ya nahi
     * 3. Signature verify karo (HMAC-SHA256)
     * 4. Event type check karo (sirf "push" handle karenge)
     * 5. Branch check karo (configured branch pe hi deploy)
     * 6. Build trigger karo
     */
    @Transactional(readOnly = true) // Transaction chahiye — project.getUser() lazy loaded hai
    public void processWebhookEvent(UUID projectId, String payload, String signatureHeader, String eventType) {
        // Step 0: Payload size check — 1MB max (GitHub typically sends <100KB)
        if (payload.length() > 1_048_576) {
            log.warn("Webhook payload too large ({} bytes) for project: {}", payload.length(), projectId);
            return;
        }

        // Step 1: Project find karo
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.warn("Webhook received for non-existent project: {}", projectId);
                    return new ApiException("Project not found", HttpStatus.NOT_FOUND);
                });

        // Step 2: Auto-deploy check
        if (!project.isAutoDeployEnabled()) {
            log.info("Auto-deploy disabled for project: {}. Ignoring webhook.", project.getName());
            return; // Silently ignore — GitHub ko 200 OK bhejna hai warna retry karega
        }

        // Step 3: Signature verify
        if (project.getWebhookSecret() == null) {
            log.warn("Webhook secret not configured for project: {}", project.getName());
            throw new ApiException("Webhook secret not configured", HttpStatus.BAD_REQUEST);
        }

        // Decrypt stored secret for HMAC verification
        String decryptedSecret;
        try {
            decryptedSecret = encryptionUtil.decrypt(project.getWebhookSecret());
        } catch (RuntimeException e) {
            log.error("Webhook secret corrupted for project {}: {}", project.getName(), e.getMessage());
            throw new ApiException("Webhook secret corrupted. Please regenerate.", HttpStatus.BAD_REQUEST);
        }

        if (!signatureVerifier.verifySignature(payload, signatureHeader, decryptedSecret)) {
            log.warn("Webhook signature verification FAILED for project: {} — possible spoofing!", project.getName());
            throw new ApiException("Invalid webhook signature", HttpStatus.UNAUTHORIZED);
        }

        // Step 3.5: Duplicate deploy prevention — already building hai toh skip
        if (project.getStatus() == com.git2go.platform.enums.ProjectStatus.BUILDING) {
            log.info("Project {} already building. Skipping auto-deploy.", project.getName());
            return;
        }

        // Step 4: Event type check — sirf "push" events pe deploy
        if (!"push".equalsIgnoreCase(eventType)) {
            log.debug("Ignoring non-push webhook event: {} for project: {}", eventType, project.getName());
            return; // Ignore silently
        }

        // Step 5: Branch check — sirf configured branch pe deploy
        String pushBranch = extractBranchFromPayload(payload);
        if (pushBranch == null) {
            log.warn("Could not extract branch from webhook payload for project: {}", project.getName());
            return;
        }

        if (!pushBranch.equals(project.getBranch())) {
            log.info("Push to branch '{}' ignored — project configured for '{}'. Project: {}",
                    pushBranch, project.getBranch(), project.getName());
            return;
        }

        // Step 6: Trigger deploy
        log.info("Auto-deploying project: {} (push to branch: {})", project.getName(), pushBranch);
        String userEmail = project.getUser().getEmail();

        try {
            buildService.triggerDeployment(project.getId(), userEmail);
            log.info("Auto-deploy triggered successfully for project: {}", project.getName());
        } catch (Exception e) {
            log.error("Auto-deploy failed for project {}: {}", project.getName(), e.getMessage());
            // Don't rethrow — GitHub ko 200 OK bhejna hai warna wo retry karega
            // Failure BuildService me logged hai already
        }
    }

    // ==================== HELPERS ====================

    /**
     * GitHub push payload se branch name extract karo.
     *
     * GitHub push payload me "ref" field hota hai: "refs/heads/main"
     * Humein sirf "main" chahiye.
     */
    private String extractBranchFromPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String ref = root.path("ref").asText(null);

            if (ref == null || !ref.startsWith("refs/heads/")) {
                return null;
            }

            // "refs/heads/main" → "main"
            return ref.substring("refs/heads/".length());
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return null;
        }
    }

    private Project getProjectAndValidate(UUID projectId, String userEmail) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException("Project not found", HttpStatus.NOT_FOUND));

        if (!project.getUser().getEmail().equals(userEmail)) {
            throw new ApiException("You don't have access to this project", HttpStatus.FORBIDDEN);
        }

        return project;
    }

    private WebhookConfigResponse buildWebhookConfig(Project project) {
        String webhookUrl = webhookBaseUrl + "/api/webhooks/github/" + project.getId();

        // Decrypt secret for user display
        String plainSecret = null;
        if (project.getWebhookSecret() != null) {
            try {
                plainSecret = encryptionUtil.decrypt(project.getWebhookSecret());
            } catch (RuntimeException e) {
                log.error("Failed to decrypt webhook secret for project {}: {}", project.getName(), e.getMessage());
                plainSecret = "ERROR: Secret corrupted. Please regenerate.";
            }
        }

        return WebhookConfigResponse.builder()
                .autoDeployEnabled(project.isAutoDeployEnabled())
                .webhookUrl(webhookUrl)
                .webhookSecret(plainSecret)
                .build();
    }
}
