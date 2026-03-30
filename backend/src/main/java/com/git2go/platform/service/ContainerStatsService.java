package com.git2go.platform.service;

import com.git2go.platform.dto.response.ContainerStatsResponse;
import com.git2go.platform.entity.Deployment;
import com.git2go.platform.entity.Project;
import com.git2go.platform.entity.User;
import com.git2go.platform.enums.DeploymentStatus;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.orchestrator.ContainerOrchestrator;
import com.git2go.platform.orchestrator.ContainerStats;
import com.git2go.platform.orchestrator.ContainerStatus;
import com.git2go.platform.repository.DeploymentRepository;
import com.git2go.platform.repository.ProjectRepository;
import com.git2go.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Container Stats Service — container metrics fetch karta hai dashboard ke liye.
 *
 * Ye Prometheus/Grafana ke UPAR hai — apne dashboard pe bhi stats dikhane ke liye.
 * Prometheus historical data ke liye, ye service real-time snapshot ke liye.
 *
 * Interview: "Do level pe monitoring hai. Prometheus + Grafana historical metrics
 * aur alerting ke liye. Apna REST API real-time container stats fetch karta hai
 * Docker Stats API se — dashboard pe CPU, memory, uptime dikhane ke liye.
 * Docker Stats API se ek snapshot lete hain — lightweight call, no persistent
 * connection needed."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerStatsService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ContainerOrchestrator containerOrchestrator;

    /**
     * Single deployment ki stats fetch karo
     */
    @Transactional(readOnly = true)
    public ContainerStatsResponse getDeploymentStats(UUID deploymentId, String userEmail) {
        Deployment deployment = getDeploymentWithAuth(deploymentId, userEmail);
        return fetchStats(deployment);
    }

    /**
     * User ke saare RUNNING deployments ki stats — dashboard overview
     */
    @Transactional(readOnly = true)
    public List<ContainerStatsResponse> getAllRunningStats(String userEmail) {
        // Sirf user ke projects — findAll() se saare users ke projects load ho jaate
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        List<Project> projects = projectRepository.findByUserId(user.getId());

        // Har project ki latest RUNNING deployment ki stats
        return projects.stream()
                .map(project -> deploymentRepository.findFirstByProjectIdOrderByCreatedAtDesc(project.getId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .filter(d -> d.getContainerId() != null && d.getStatus() == DeploymentStatus.RUNNING)
                .map(this::fetchStats)
                .toList();
    }

    // ==================== PRIVATE ====================

    private ContainerStatsResponse fetchStats(Deployment deployment) {
        String containerId = deployment.getContainerId();
        Project project = deployment.getProject();

        // Default values — agar container nahi chal raha toh
        if (containerId == null) {
            return buildEmptyStats(deployment, project, "No container");
        }

        try {
            // Container status check
            ContainerStatus status = containerOrchestrator.getContainerStatus(containerId);

            if (status == ContainerStatus.NOT_FOUND) {
                return buildEmptyStats(deployment, project, "Container not found");
            }

            if (status != ContainerStatus.RUNNING) {
                return ContainerStatsResponse.builder()
                        .deploymentId(deployment.getId())
                        .projectId(project.getId())
                        .projectName(project.getName())
                        .containerStatus(status.name())
                        .cpuUsagePercent(0)
                        .memoryUsageMb(0)
                        .memoryLimitMb(0)
                        .memoryUsagePercent(0)
                        .uptime("0m")
                        .collectedAt(LocalDateTime.now())
                        .build();
            }

            // Stats fetch — CPU, Memory, Network
            ContainerStats stats = containerOrchestrator.getContainerStats(containerId);

            // Memory percentage calculate
            double memPercent = stats.getMemoryLimitMb() > 0
                    ? (double) stats.getMemoryUsageMb() / stats.getMemoryLimitMb() * 100
                    : 0;

            // Uptime calculate
            String uptime = calculateUptime(deployment.getCreatedAt());

            return ContainerStatsResponse.builder()
                    .deploymentId(deployment.getId())
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .containerStatus(status.name())
                    .cpuUsagePercent(stats.getCpuUsagePercent())
                    .memoryUsageMb(stats.getMemoryUsageMb())
                    .memoryLimitMb(stats.getMemoryLimitMb())
                    .memoryUsagePercent(Math.round(memPercent * 100.0) / 100.0) // 2 decimal
                    .networkInputBytes(stats.getNetworkInputBytes())
                    .networkOutputBytes(stats.getNetworkOutputBytes())
                    .uptime(uptime)
                    .collectedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch stats for deployment {}: {}", deployment.getId(), e.getMessage());
            return buildEmptyStats(deployment, project, "Error: " + e.getMessage());
        }
    }

    private ContainerStatsResponse buildEmptyStats(Deployment deployment, Project project, String status) {
        return ContainerStatsResponse.builder()
                .deploymentId(deployment.getId())
                .projectId(project.getId())
                .projectName(project.getName())
                .containerStatus(status)
                .cpuUsagePercent(0)
                .memoryUsageMb(0)
                .memoryLimitMb(0)
                .memoryUsagePercent(0)
                .collectedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Uptime calculate — "3d 5h 12m" format
     */
    private String calculateUptime(LocalDateTime startTime) {
        if (startTime == null) return "0m";

        Duration duration = Duration.between(startTime, LocalDateTime.now());
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private Deployment getDeploymentWithAuth(UUID deploymentId, String userEmail) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ApiException("Deployment not found", HttpStatus.NOT_FOUND));

        if (!deployment.getProject().getUser().getEmail().equals(userEmail)) {
            throw new ApiException("You don't have access to this deployment", HttpStatus.FORBIDDEN);
        }

        return deployment;
    }
}
