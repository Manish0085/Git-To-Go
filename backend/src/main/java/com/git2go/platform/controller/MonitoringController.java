package com.git2go.platform.controller;

import com.git2go.platform.dto.response.ContainerStatsResponse;
import com.git2go.platform.dto.response.HealthCheckResponse;
import com.git2go.platform.service.ContainerStatsService;
import com.git2go.platform.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Monitoring Controller
 *
 * GET  /api/monitoring/stats                      → All running containers stats
 * GET  /api/monitoring/deployments/{id}/stats     → Single deployment stats
 * GET  /api/monitoring/deployments/{id}/health    → Health check (manual trigger)
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final ContainerStatsService containerStatsService;
    private final HealthCheckService healthCheckService;

    @GetMapping("/stats")
    public ResponseEntity<List<ContainerStatsResponse>> getAllStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ContainerStatsResponse> stats = containerStatsService.getAllRunningStats(userDetails.getUsername());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/deployments/{deploymentId}/stats")
    public ResponseEntity<ContainerStatsResponse> getDeploymentStats(
            @PathVariable UUID deploymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ContainerStatsResponse stats = containerStatsService.getDeploymentStats(deploymentId, userDetails.getUsername());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/deployments/{deploymentId}/health")
    public ResponseEntity<HealthCheckResponse> checkHealth(
            @PathVariable UUID deploymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        HealthCheckResponse health = healthCheckService.checkHealth(deploymentId, userDetails.getUsername());
        return ResponseEntity.ok(health);
    }
}
