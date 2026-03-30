package com.git2go.platform.service;

import com.git2go.platform.dto.response.HealthCheckResponse;
import com.git2go.platform.entity.Deployment;
import com.git2go.platform.enums.DeploymentStatus;
import com.git2go.platform.enums.ProjectStatus;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.orchestrator.ContainerOrchestrator;
import com.git2go.platform.orchestrator.ContainerStatus;
import com.git2go.platform.repository.DeploymentRepository;
import com.git2go.platform.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health Check Service — periodically running containers ki health verify karta hai.
 *
 * 2 types of checks:
 * 1. Docker status check — container alive hai ya crash hua
 * 2. HTTP health check — app actually respond kar rahi hai ya nahi
 *
 * Consecutive failure tracking — 3 failures pe FAILED mark
 *
 * Interview: "Scheduled health check har 60 seconds chalta hai. Docker inspect se
 * container alive check karta hoon. Phir HTTP GET se application responsiveness
 * verify karta hoon. Consecutive failure counter maintain karta hoon —
 * 3 failures pe status FAILED mark hota hai aur project status update hota hai.
 * ConcurrentHashMap me failure count track hota hai — thread-safe."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final ContainerOrchestrator containerOrchestrator;

    // Track consecutive failures per deployment
    private final Map<UUID, Integer> failureCounters = new ConcurrentHashMap<>();

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 5000; // 5 seconds

    /**
     * Scheduled health check — har 60 seconds me saare RUNNING deployments check
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000) // 60s interval, 30s initial delay
    @Transactional
    public void checkAllRunningDeployments() {
        // Proper DB query — findAll() se saare deployments load ho jaate, inefficient
        List<Deployment> runningDeployments = deploymentRepository.findByStatus(DeploymentStatus.RUNNING).stream()
                .filter(d -> d.getContainerId() != null)
                .toList();

        if (runningDeployments.isEmpty()) return;

        log.debug("Running health check for {} deployments", runningDeployments.size());

        for (Deployment deployment : runningDeployments) {
            try {
                checkDeploymentHealth(deployment);
            } catch (Exception e) {
                log.error("Health check error for deployment {}: {}", deployment.getId(), e.getMessage());
            }
        }
    }

    /**
     * Manual health check — user API se trigger kar sakta hai
     */
    @Transactional(readOnly = true)
    public HealthCheckResponse checkHealth(UUID deploymentId, String userEmail) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ApiException("Deployment not found", HttpStatus.NOT_FOUND));

        if (!deployment.getProject().getUser().getEmail().equals(userEmail)) {
            throw new ApiException("You don't have access to this deployment", HttpStatus.FORBIDDEN);
        }

        return performHealthCheck(deployment);
    }

    // ==================== PRIVATE ====================

    private void checkDeploymentHealth(Deployment deployment) {
        HealthCheckResponse result = performHealthCheck(deployment);

        if (result.isHealthy()) {
            // Healthy — reset failure counter
            failureCounters.remove(deployment.getId());
        } else {
            // Unhealthy — increment failure counter
            int failures = failureCounters.merge(deployment.getId(), 1, Integer::sum);
            log.warn("Health check failed for deployment {} ({}/{}): {}",
                    deployment.getId(), failures, MAX_CONSECUTIVE_FAILURES, result.getMessage());

            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                // 3 consecutive failures — mark as FAILED
                log.error("Deployment {} marked FAILED after {} consecutive health check failures",
                        deployment.getId(), failures);

                deployment.setStatus(DeploymentStatus.FAILED);
                deployment.setFailureReason("Health check failed " + failures + " consecutive times: " + result.getMessage());
                deploymentRepository.save(deployment);

                deployment.getProject().setStatus(ProjectStatus.FAILED);
                projectRepository.save(deployment.getProject());

                failureCounters.remove(deployment.getId());
            }
        }
    }

    private HealthCheckResponse performHealthCheck(Deployment deployment) {
        UUID deploymentId = deployment.getId();
        String containerId = deployment.getContainerId();

        if (containerId == null) {
            return buildResponse(deploymentId, "UNKNOWN", false, "No container", 0, 0);
        }

        // Check 1: Docker container status
        ContainerStatus containerStatus;
        try {
            containerStatus = containerOrchestrator.getContainerStatus(containerId);
        } catch (Exception e) {
            return buildResponse(deploymentId, "ERROR", false, "Docker check failed: " + e.getMessage(), 0, 0);
        }

        if (containerStatus != ContainerStatus.RUNNING) {
            return buildResponse(deploymentId, containerStatus.name(), false,
                    "Container not running: " + containerStatus, 0, 0);
        }

        // Check 2: HTTP health check — app respond kar raha hai?
        int hostPort = deployment.getHostPort();
        if (hostPort <= 0) {
            return buildResponse(deploymentId, "RUNNING", true, "Container running (no HTTP check — port not mapped)", 0, 0);
        }

        long startTime = System.currentTimeMillis();
        try {
            URI uri = URI.create("http://localhost:" + hostPort + "/");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(HEALTH_CHECK_TIMEOUT_MS);
            conn.setReadTimeout(HEALTH_CHECK_TIMEOUT_MS);

            int statusCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            conn.disconnect();

            // 2xx ya 3xx = healthy
            boolean healthy = statusCode >= 200 && statusCode < 400;

            return buildResponse(deploymentId, "RUNNING", healthy,
                    healthy ? "HTTP " + statusCode + " OK" : "HTTP " + statusCode,
                    statusCode, responseTime);

        } catch (java.net.ConnectException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return buildResponse(deploymentId, "RUNNING", false,
                    "Connection refused — app may still be starting", 0, responseTime);
        } catch (java.net.SocketTimeoutException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return buildResponse(deploymentId, "RUNNING", false,
                    "Timeout — app not responding within " + HEALTH_CHECK_TIMEOUT_MS + "ms", 0, responseTime);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return buildResponse(deploymentId, "RUNNING", false,
                    "Health check error: " + e.getMessage(), 0, responseTime);
        }
    }

    private HealthCheckResponse buildResponse(UUID deploymentId, String containerStatus,
                                               boolean healthy, String message,
                                               int httpStatus, long responseTime) {
        return HealthCheckResponse.builder()
                .deploymentId(deploymentId)
                .containerStatus(containerStatus)
                .healthy(healthy)
                .message(message)
                .httpStatusCode(httpStatus)
                .responseTimeMs(responseTime)
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
