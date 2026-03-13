package com.deployment.Git2Go.service.impl;

import com.deployment.Git2Go.dto.request.DeploymentRequest;
import com.deployment.Git2Go.dto.response.DeploymentResponse;
import com.deployment.Git2Go.entity.Deployment;
import com.deployment.Git2Go.entity.Project;
import com.deployment.Git2Go.entity.User;
import com.deployment.Git2Go.enums.BuildStatus;
import com.deployment.Git2Go.enums.TechStack;
import com.deployment.Git2Go.repository.DeploymentRepository;
import com.deployment.Git2Go.repository.ProjectRepository;
import com.deployment.Git2Go.repository.UserRepository;
import com.deployment.Git2Go.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentServiceImpl implements DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository    projectRepository;
    private final UserRepository       userRepository;
    private final GitService           gitService;
    private final TechDetectionService techDetectionService;
    private final CodeBuildService     codeBuildService;
    private final EcsDeployService     ecsDeployService;
    private final LogStreamingService  logStreamingService;

    @Override
    public DeploymentResponse triggerDeployment(DeploymentRequest request,
                                                 String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Project project = projectRepository.findById(request.getProjectId())
            .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUserId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Create deployment record
        Deployment deployment = Deployment.builder()
            .projectId(project.getId())
            .repoUrl(project.getRepoUrl())
            .branch(request.getBranch() != null
                ? request.getBranch() : project.getBranch())
            .commitMessage(request.getCommitMessage())
            .buildStatus(BuildStatus.QUEUED)
            .triggeredBy("manual")
            .build();

        deploymentRepository.save(deployment);

        // Run pipeline async so API returns immediately
        runPipelineAsync(deployment.getId());

        return toResponse(deployment, project.getName());
    }

    // Called by WebhookController for auto-deploy on git push
    public void triggerFromWebhook(Project project,
                                    String commitSha,
                                    String commitMessage) {
        Deployment deployment = Deployment.builder()
            .projectId(project.getId())
            .repoUrl(project.getRepoUrl())
            .branch(project.getBranch())
            .commitSha(commitSha)
            .commitMessage(commitMessage)
            .buildStatus(BuildStatus.QUEUED)
            .triggeredBy("webhook")
            .build();

        deploymentRepository.save(deployment);
        runPipelineAsync(deployment.getId());
    }

    @Override
    public DeploymentResponse getDeployment(String deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new RuntimeException("Deployment not found"));

        Project project = projectRepository.findById(deployment.getProjectId())
            .orElseThrow(() -> new RuntimeException("Project not found"));

        return toResponse(deployment, project.getName());
    }

    @Override
    public List<DeploymentResponse> getProjectDeployments(String projectId,
                                                            String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUserId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return deploymentRepository
            .findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(d -> toResponse(d, project.getName()))
            .collect(Collectors.toList());
    }

    @Override
    public void rollback(String deploymentId, String userEmail) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new RuntimeException("Deployment not found"));

        if (deployment.getPreviousTaskDefArn() == null) {
            throw new RuntimeException("No previous version to rollback to");
        }

        Project project = projectRepository
            .findById(deployment.getProjectId())
            .orElseThrow(() -> new RuntimeException("Project not found"));

        ecsDeployService.rollback(
            project.getId(),
            deployment.getPreviousTaskDefArn(),
            deploymentId,
            logStreamingService
        );

        deployment.setBuildStatus(BuildStatus.ROLLED_BACK);
        deploymentRepository.save(deployment);
    }

    // ── ASYNC PIPELINE ────────────────────────────────────────

    @Async
    public void runPipelineAsync(String deploymentId) {
        Path workDir = null;
        Deployment deployment = null;

        try {
            deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() ->
                    new RuntimeException("Deployment not found"));

            Project project = projectRepository
                .findById(deployment.getProjectId())
                .orElseThrow(() ->
                    new RuntimeException("Project not found"));

            // ── STEP 1: Clone ──────────────────────────────
            updateStatus(deployment, BuildStatus.CLONING);
            logStreamingService.pushLog(deploymentId,
                "🔁 Cloning repository: " + project.getRepoUrl());

            workDir = gitService.cloneRepo(
                project.getRepoUrl(),
                deployment.getBranch(),
                deploymentId
            );

            // ── STEP 2: Detect tech stack ──────────────────
            updateStatus(deployment, BuildStatus.DETECTING);
            logStreamingService.pushLog(deploymentId,
                "🔍 Detecting tech stack...");

            TechStack techStack = techDetectionService.detect(workDir);
            deployment.setDetectedTechStack(techStack.name());
            project.setDetectedTechStack(techStack.name());

            logStreamingService.pushLog(deploymentId,
                "✅ Detected: " + techStack);

            // Write generated Dockerfile into repo
            String dockerfile = techDetectionService
                .generateDockerfile(techStack);
            Files.writeString(
                workDir.resolve("Dockerfile"), dockerfile);

            logStreamingService.pushLog(deploymentId,
                "📄 Dockerfile generated for " + techStack);

            // ── STEP 3: Build image via CodeBuild ──────────
            updateStatus(deployment, BuildStatus.BUILDING);

            String imageTag = deploymentId.substring(0, 8);
            String buildId  = codeBuildService.startBuild(
                workDir, deploymentId, imageTag, logStreamingService);

            String imageUri = codeBuildService.waitForBuild(
                buildId, deploymentId, imageTag, logStreamingService);

            deployment.setImageUri(imageUri);

            // ── STEP 4: Deploy to ECS ──────────────────────
            updateStatus(deployment, BuildStatus.DEPLOYING);

            // Save previous task def ARN for rollback ⭐
            String previousTaskDefArn = ecsDeployService
                .getCurrentTaskDefArn(project.getId());
            deployment.setPreviousTaskDefArn(previousTaskDefArn);
            project.setLastStableTaskDefArn(previousTaskDefArn);

            String newTaskDefArn = ecsDeployService.deploy(
                project.getId(), imageUri,
                deploymentId, logStreamingService);

            deployment.setEcsTaskDefArn(newTaskDefArn);

            // ── STEP 5: Wait for stability ─────────────────
            boolean healthy = ecsDeployService.waitForStability(
                project.getId(), deploymentId, logStreamingService);

            if (healthy) {
                // ✅ SUCCESS
                deployment.setBuildStatus(BuildStatus.LIVE);
                project.setLastStableTaskDefArn(newTaskDefArn);
                project.setLastStableImageUri(imageUri);
                projectRepository.save(project);

                logStreamingService.pushLog(deploymentId,
                    "🎉 Deployment LIVE!");

            } else {
                // ❌ FAILED — AUTO ROLLBACK ⭐
                logStreamingService.pushLog(deploymentId,
                    "❌ Health check failed. Starting auto-rollback...");

                deployment.setBuildStatus(BuildStatus.FAILED);

                if (previousTaskDefArn != null) {
                    ecsDeployService.rollback(
                        project.getId(),
                        previousTaskDefArn,
                        deploymentId,
                        logStreamingService
                    );
                    deployment.setBuildStatus(BuildStatus.ROLLED_BACK);
                    logStreamingService.pushLog(deploymentId,
                        "🔄 Rolled back to previous stable version");
                } else {
                    logStreamingService.pushLog(deploymentId,
                        "⚠️ No previous version to rollback to");
                }
            }

        } catch (Exception e) {
            log.error("Deployment pipeline failed: {}", e.getMessage(), e);

            if (deployment != null) {
                deployment.setBuildStatus(BuildStatus.FAILED);
                logStreamingService.pushLog(deploymentId,
                    "❌ Pipeline error: " + e.getMessage());
                deploymentRepository.save(deployment);
            }

        } finally {
            // Always cleanup workspace
            if (workDir != null) {
                gitService.cleanup(workDir);
            }
            // Close SSE connection
            logStreamingService.complete(deploymentId);

            if (deployment != null) {
                deploymentRepository.save(deployment);
            }
        }
    }

    private void updateStatus(Deployment deployment, BuildStatus status) {
        deployment.setBuildStatus(status);
        deploymentRepository.save(deployment);
        logStreamingService.pushLog(deployment.getId(),
            "📌 Status: " + status);
    }

    private DeploymentResponse toResponse(Deployment d, String projectName) {
        return DeploymentResponse.builder()
            .id(d.getId())
            .projectId(d.getProjectId())
            .projectName(projectName)
            .repoUrl(d.getRepoUrl())
            .branch(d.getBranch())
            .commitSha(d.getCommitSha())
            .commitMessage(d.getCommitMessage())
            .detectedTechStack(d.getDetectedTechStack())
            .buildStatus(d.getBuildStatus())
            .appUrl(d.getAppUrl())
            .triggeredBy(d.getTriggeredBy())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            .build();
    }
}