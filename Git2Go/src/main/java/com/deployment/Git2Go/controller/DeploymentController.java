package com.deployment.Git2Go.controller;

import com.deployment.Git2Go.dto.request.DeploymentRequest;
import com.deployment.Git2Go.dto.response.ApiResponse;
import com.deployment.Git2Go.dto.response.DeploymentResponse;
import com.deployment.Git2Go.service.DeploymentService;
import com.deployment.Git2Go.service.impl.LogStreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final LogStreamingService logStreamingService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeploymentResponse>> deploy(
            @Valid @RequestBody DeploymentRequest request,
            Authentication authentication) {

        DeploymentResponse response = deploymentService
            .triggerDeployment(request, authentication.getName());

        return ResponseEntity.ok(
            ApiResponse.success("Deployment started", response));
    }

    @GetMapping("/{deploymentId}")
    public ResponseEntity<ApiResponse<DeploymentResponse>> getDeployment(
            @PathVariable String deploymentId) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deploymentService.getDeployment(deploymentId)));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<DeploymentResponse>>>
            getProjectDeployments(
            @PathVariable String projectId,
            Authentication authentication) {

        List<DeploymentResponse> deployments = deploymentService
            .getProjectDeployments(projectId, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(deployments));
    }

    @PostMapping("/{deploymentId}/rollback")
    public ResponseEntity<ApiResponse<Void>> rollback(
            @PathVariable String deploymentId,
            Authentication authentication) {

        deploymentService.rollback(deploymentId, authentication.getName());

        return ResponseEntity.ok(
            ApiResponse.success("Rollback initiated", null));
    }

    // ⭐ Live log streaming via SSE
    @GetMapping(
        value = "/{deploymentId}/logs",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamLogs(@PathVariable String deploymentId) {
        return logStreamingService.subscribe(deploymentId);
    }

    // Get stored logs (for page refresh)
    @GetMapping("/{deploymentId}/logs/history")
    public ResponseEntity<ApiResponse<List<String>>> getLogHistory(
            @PathVariable String deploymentId) {

        return ResponseEntity.ok(
            ApiResponse.success(
                logStreamingService.getLogs(deploymentId)));
    }
}