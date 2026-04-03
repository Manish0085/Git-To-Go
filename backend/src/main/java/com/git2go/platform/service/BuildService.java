package com.git2go.platform.service;

import com.git2go.platform.audit.Auditable;
import com.git2go.platform.dto.response.BuildLogResponse;
import com.git2go.platform.dto.response.DeploymentResponse;
import com.git2go.platform.entity.Deployment;
import com.git2go.platform.entity.EnvVariable;
import com.git2go.platform.entity.Project;
import com.git2go.platform.enums.DeploymentStatus;
import com.git2go.platform.enums.ProjectLanguage;
import com.git2go.platform.enums.ProjectStatus;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.logging.LogEntry;
import com.git2go.platform.logging.LogStorageService;
import com.git2go.platform.orchestrator.BuildRequest;
import com.git2go.platform.orchestrator.ContainerOrchestrator;
import com.git2go.platform.orchestrator.RunContainerRequest;
import com.git2go.platform.repository.DeploymentRepository;
import com.git2go.platform.repository.ProjectRepository;
import com.git2go.platform.util.PortAllocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Build Service — Full async build + deploy pipeline.
 *
 * Interview: "Build pipeline @Async se background me chalti hai. Controller turant
 * deployment ID return karta hai (202 Accepted). Client polling se status track karta hai.
 * Lazy loading issues avoid karne ke liye saara data eagerly load karke ek DTO me pass
 * karta hoon async method ko — kyunki Hibernate session transactional method ke baad
 * close ho jaata hai."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final LogStorageService logStorageService;
    private final ContainerOrchestrator containerOrchestrator;
    private final LanguageDetectorService languageDetector;
    private final DockerfileGeneratorService dockerfileGenerator;
    private final PortAllocator portAllocator;
    private final EmailService emailService;

    @Value("${app.builds.base-dir:/tmp/git2go/builds}")
    private String buildsBaseDir;

    @Value("${app.deployment.base-url:http://localhost}")
    private String deploymentBaseUrl;

    private static final long GIT_CLONE_TIMEOUT_MINUTES = 5;
    // Allowed repo URL patterns — sirf HTTPS GitHub/GitLab URLs allow
    private static final Pattern VALID_REPO_URL = Pattern.compile("^https://[\\w.-]+\\.[a-z]{2,}/[\\w./-]+\\.git$|^https://[\\w.-]+\\.[a-z]{2,}/[\\w./-]+$");
    // Branch name validation — alphanumeric, dash, underscore, dot, slash allowed
    private static final Pattern VALID_BRANCH = Pattern.compile("^[\\w./-]+$");

    /**
     * Deploy trigger — Deployment entity create + async build start.
     *
     * IMPORTANT: Saara project data eagerly load karo yahan kyunki @Async method
     * me Hibernate session nahi hogi — LazyInitializationException se bachne ke liye.
     */
    @Transactional
    @Auditable(action = "DEPLOY_PROJECT")
    public DeploymentResponse triggerDeployment(UUID projectId, String userEmail) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException("Project not found", HttpStatus.NOT_FOUND));

        if (!project.getUser().getEmail().equals(userEmail)) {
            throw new ApiException("You don't have access to this project", HttpStatus.FORBIDDEN);
        }

        // Validate repo URL
        if (!VALID_REPO_URL.matcher(project.getRepoUrl()).matches()) {
            throw new ApiException("Invalid repository URL. Only HTTPS Git URLs are allowed.", HttpStatus.BAD_REQUEST);
        }

        int version = deploymentRepository.countByProjectId(projectId) + 1;

        Deployment deployment = Deployment.builder()
                .project(project)
                .version(version)
                .status(DeploymentStatus.QUEUED)
                .build();
        deployment = deploymentRepository.save(deployment);

        project.setStatus(ProjectStatus.BUILDING);
        projectRepository.save(project);

        // EAGERLY load all data into a plain map — async method me lazy loading nahi chalegi
        Map<String, String> envVars = new HashMap<>();
        if (project.getEnvVariables() != null) {
            for (EnvVariable ev : project.getEnvVariables()) {
                envVars.put(ev.getKey(), ev.getValue());
            }
        }

        // Pass all data as simple values — NO entity references in async method
        executeBuildPipeline(
                deployment.getId(),
                project.getId(),
                project.getName(),
                project.getRepoUrl(),
                project.getBranch(),
                project.getPort(),
                version,
                envVars,
                userEmail
        );

        return mapToResponse(deployment);
    }

    /**
     * ASYNC — Background build pipeline.
     *
     * Receives ALL data as simple parameters — no entity references.
     * Fetches fresh entities from DB when needed for status updates.
     * Cleans up temp directory on failure.
     */
    @Async("buildExecutor")
    public void executeBuildPipeline(UUID deploymentId, UUID projectId, String projectName,
                                     String repoUrl, String branch, int containerPort,
                                     int version, Map<String, String> envVars, String userEmail) {

        Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null) {
            log.error("Deployment not found: {}", deploymentId);
            return;
        }

        // Sanitize project name for Docker — replace runs of invalid chars with single dash
        String sanitizedName = projectName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Path projectDir = Path.of(buildsBaseDir, projectId.toString(), String.valueOf(version));

        try {
            // ===== STEP 1: GIT CLONE =====
            updateStatus(deployment, DeploymentStatus.CLONING);
            addLog(deployment, "INFO", "Cloning repository: " + repoUrl + " (branch: " + branch + ")");

            cloneRepository(repoUrl, branch, projectDir);
            addLog(deployment, "INFO", "Repository cloned successfully");

            // ===== STEP 2: DETECT LANGUAGE =====
            addLog(deployment, "INFO", "Detecting project language...");
            ProjectLanguage language = languageDetector.detectLanguage(projectDir);

            if (language == ProjectLanguage.UNKNOWN && !languageDetector.hasDockerfile(projectDir)) {
                throw new ApiException("Could not detect language and no Dockerfile found in repository.", HttpStatus.BAD_REQUEST);
            }
            addLog(deployment, "INFO", "Detected language: " + language);

            // ===== STEP 2.5: AUTO-DETECT PORT =====
            if (containerPort <= 0) {
                // User ne port nahi diya — auto-detect karo
                containerPort = languageDetector.detectPort(projectDir, language);
                addLog(deployment, "INFO", "Auto-detected port: " + containerPort);
            } else {
                // User ne port diya — verify karo config se
                int detectedPort = languageDetector.detectPort(projectDir, language);
                if (detectedPort != containerPort) {
                    addLog(deployment, "WARN", "User port (" + containerPort + ") differs from detected (" + detectedPort + "). Using detected port.");
                    containerPort = detectedPort;
                }
            }

            // Update project port in DB so UI shows correct port
            Project proj = projectRepository.findById(projectId).orElse(null);
            if (proj != null && proj.getPort() != containerPort) {
                proj.setPort(containerPort);
                projectRepository.save(proj);
            }

            // ===== STEP 3: GENERATE DOCKERFILE =====
            if (!languageDetector.hasDockerfile(projectDir)) {
                addLog(deployment, "INFO", "No Dockerfile found. Generating for " + language);
                dockerfileGenerator.generateDockerfile(projectDir, language, containerPort);
                addLog(deployment, "INFO", "Dockerfile generated successfully");
            } else if (language != ProjectLanguage.UNKNOWN && !isMultiStageDockerfile(projectDir)) {
                addLog(deployment, "WARN", "Existing Dockerfile is single-stage (no build step). Replacing with multi-stage Dockerfile for " + language);
                dockerfileGenerator.generateDockerfile(projectDir, language, containerPort);
                addLog(deployment, "INFO", "Multi-stage Dockerfile generated successfully");
            } else {
                addLog(deployment, "INFO", "Using existing Dockerfile from repository");
            }

            // ===== STEP 4: DOCKER BUILD =====
            updateStatus(deployment, DeploymentStatus.BUILDING);
            String imageName = "git2go/" + sanitizedName + ":v" + version;
            addLog(deployment, "INFO", "Building Docker image: " + imageName);

            containerOrchestrator.buildImage(BuildRequest.builder()
                    .buildDirectory(projectDir.toString())
                    .imageName(imageName)
                    .dockerfilePath(projectDir.resolve("Dockerfile").toString())
                    .build());

            deployment.setImageId(imageName);
            deploymentRepository.save(deployment);
            addLog(deployment, "INFO", "Docker image built successfully");

            // ===== STEP 4.5: DETECT PORT FROM BUILT IMAGE =====
            // Image build ho chuki hai — ab image inspect se actual EXPOSE port nikalo
            // Ye sabse reliable hai kyunki Dockerfile (user ka ya generated) already processed hai
            int imagePort = containerOrchestrator.getExposedPort(imageName);
            if (imagePort > 0 && imagePort != containerPort) {
                addLog(deployment, "INFO", "Port from built image: " + imagePort + " (was: " + containerPort + ")");
                containerPort = imagePort;
                proj = projectRepository.findById(projectId).orElse(null);
                if (proj != null) {
                    proj.setPort(containerPort);
                    projectRepository.save(proj);
                }
            }

            // ===== STEP 5: STOP OLD CONTAINER =====
            stopOldContainer(projectId);

            // ===== STEP 6: DOCKER RUN =====
            updateStatus(deployment, DeploymentStatus.DEPLOYING);
            int hostPort = portAllocator.allocatePort();
            String containerName = "git2go-" + sanitizedName + "-v" + version;

            addLog(deployment, "INFO", "Starting container " + containerName + " on port " + hostPort);

            String containerId = containerOrchestrator.runContainer(RunContainerRequest.builder()
                    .imageName(imageName)
                    .containerName(containerName)
                    .hostPort(hostPort)
                    .containerPort(containerPort)
                    .envVariables(envVars)
                    .memoryLimitMb(512)
                    .cpuLimit(0.5)
                    .build());

            // ===== STEP 7: UPDATE STATUS =====
            String deployedUrl = deploymentBaseUrl + ":" + hostPort;
            deployment.setContainerId(containerId);
            deployment.setContainerName(containerName);
            deployment.setHostPort(hostPort);
            deployment.setDeployedUrl(deployedUrl);
            updateStatus(deployment, DeploymentStatus.RUNNING);

            // Update project — fresh fetch to avoid stale entity
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                project.setStatus(ProjectStatus.RUNNING);
                project.setDeployedUrl(deployedUrl);
                projectRepository.save(project);
            }

            addLog(deployment, "INFO", "Deployment successful! App is live at " + deployedUrl);
            log.info("Deployment successful: project={} url={}", projectName, deployedUrl);

            // Email notification — success
            emailService.sendDeploymentSuccessEmail(userEmail, projectName, deployedUrl, version);

        } catch (Exception e) {
            log.error("Build pipeline failed for project {}: {}", projectName, e.getMessage());
            deployment.setFailureReason(e.getMessage());
            updateStatus(deployment, DeploymentStatus.FAILED);

            Project project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                project.setStatus(ProjectStatus.FAILED);
                projectRepository.save(project);
            }

            addLog(deployment, "ERROR", "Deployment failed: " + e.getMessage());

            // Email notification — failure
            emailService.sendDeploymentFailedEmail(userEmail, projectName, e.getMessage(), version);
        } finally {
            // Cleanup temp build directory — success ya failure dono me
            cleanupBuildDirectory(projectDir);
        }
    }

    /**
     * Git clone with timeout + input validation.
     */
    private void cloneRepository(String repoUrl, String branch, Path targetDir) {
        // Validate branch name
        if (!VALID_BRANCH.matcher(branch).matches()) {
            throw new ApiException("Invalid branch name: " + branch, HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(targetDir);

            ProcessBuilder pb = new ProcessBuilder(
                    "git", "clone", "--branch", branch, "--depth", "1", repoUrl, targetDir.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            // TIMEOUT — 5 minutes max, phir kill
            boolean completed = process.waitFor(GIT_CLONE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new ApiException("Git clone timed out after " + GIT_CLONE_TIMEOUT_MINUTES + " minutes", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new ApiException("Git clone failed: " + output, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Git clone failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Purana container stop + remove (redeploy ke case me)
     */
    private void stopOldContainer(UUID projectId) {
        deploymentRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .filter(d -> d.getContainerId() != null && d.getStatus() == DeploymentStatus.RUNNING)
                .ifPresent(oldDeployment -> {
                    try {
                        log.info("Stopping old container: {}", oldDeployment.getContainerId());
                        containerOrchestrator.stopContainer(oldDeployment.getContainerId());
                        containerOrchestrator.removeContainer(oldDeployment.getContainerId());
                        oldDeployment.setStatus(DeploymentStatus.STOPPED);
                        deploymentRepository.save(oldDeployment);
                    } catch (Exception e) {
                        log.warn("Failed to stop old container: {}", e.getMessage());
                    }
                });
    }

    /**
     * Temp build directory cleanup — disk space leak prevent karo
     */
    private void cleanupBuildDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                // Recursive delete
                ProcessBuilder pb = new ProcessBuilder("rm", "-rf", directory.toString());
                pb.start().waitFor(1, TimeUnit.MINUTES);
                log.info("Cleaned up build directory: {}", directory);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup build directory {}: {}", directory, e.getMessage());
        }
    }

    // ==================== GET DEPLOYMENT INFO ====================

    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(UUID deploymentId, String userEmail) {
        Deployment deployment = getDeploymentAndValidate(deploymentId, userEmail);
        return mapToResponse(deployment);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getProjectDeployments(UUID projectId, String userEmail) {
        // Authorization check — project owner hi deployments dekh sakta hai
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException("Project not found", HttpStatus.NOT_FOUND));
        if (!project.getUser().getEmail().equals(userEmail)) {
            throw new ApiException("You don't have access to this project", HttpStatus.FORBIDDEN);
        }

        return deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BuildLogResponse> getDeploymentLogs(UUID deploymentId, String userEmail) {
        // Authorization check
        getDeploymentAndValidate(deploymentId, userEmail);

        // Read from file system (not DB)
        return logStorageService.readLogs(deploymentId).stream()
                .map(entry -> BuildLogResponse.builder()
                        .message(entry.getMessage())
                        .level(entry.getLevel())
                        .timestamp(entry.getTimestamp())
                        .build())
                .toList();
    }

    // ==================== CONTAINER OPERATIONS ====================

    @Transactional
    @Auditable(action = "STOP_DEPLOYMENT")
    public void stopDeployment(UUID deploymentId, String userEmail) {
        Deployment deployment = getDeploymentAndValidate(deploymentId, userEmail);
        if (deployment.getContainerId() != null) {
            containerOrchestrator.stopContainer(deployment.getContainerId());
        }
        updateStatus(deployment, DeploymentStatus.STOPPED);
        deployment.getProject().setStatus(ProjectStatus.STOPPED);
        projectRepository.save(deployment.getProject());
    }

    @Transactional
    @Auditable(action = "RESTART_DEPLOYMENT")
    public void restartDeployment(UUID deploymentId, String userEmail) {
        Deployment deployment = getDeploymentAndValidate(deploymentId, userEmail);
        if (deployment.getContainerId() != null) {
            containerOrchestrator.restartContainer(deployment.getContainerId());
        }
        updateStatus(deployment, DeploymentStatus.RUNNING);
        deployment.getProject().setStatus(ProjectStatus.RUNNING);
        projectRepository.save(deployment.getProject());
    }

    // ==================== HELPERS ====================

    private Deployment getDeploymentAndValidate(UUID deploymentId, String userEmail) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ApiException("Deployment not found", HttpStatus.NOT_FOUND));
        if (!deployment.getProject().getUser().getEmail().equals(userEmail)) {
            log.warn("Unauthorized deployment access: user={} deployment={}", userEmail, deploymentId);
            throw new ApiException("You don't have access to this deployment", HttpStatus.FORBIDDEN);
        }
        return deployment;
    }

    private void updateStatus(Deployment deployment, DeploymentStatus status) {
        deployment.setStatus(status);
        deploymentRepository.save(deployment);
    }

    private void addLog(Deployment deployment, String level, String message) {
        // Write to file system — NOT to DB
        logStorageService.appendLog(deployment.getId(), level, message);
    }

    /**
     * Check if existing Dockerfile is multi-stage (has build step like "FROM ... AS builder").
     * Single-stage Dockerfiles assume pre-built artifacts (e.g., target/*.jar) which won't
     * exist after a fresh git clone — so we need to replace them with our multi-stage version.
     */
    private boolean isMultiStageDockerfile(Path projectDir) {
        try {
            String content = Files.readString(projectDir.resolve("Dockerfile")).toUpperCase();
            long fromCount = content.lines()
                    .filter(line -> line.trim().startsWith("FROM"))
                    .count();
            return fromCount >= 2; // Multi-stage = 2+ FROM instructions
        } catch (Exception e) {
            log.warn("Failed to read Dockerfile: {}", e.getMessage());
            return true; // Assume multi-stage if can't read — don't override
        }
    }

    private DeploymentResponse mapToResponse(Deployment deployment) {
        return DeploymentResponse.builder()
                .id(deployment.getId())
                .projectId(deployment.getProject().getId())
                .version(deployment.getVersion())
                .status(deployment.getStatus())
                .deployedUrl(deployment.getDeployedUrl())
                .failureReason(deployment.getFailureReason())
                .createdAt(deployment.getCreatedAt())
                .updatedAt(deployment.getUpdatedAt())
                .build();
    }
}
