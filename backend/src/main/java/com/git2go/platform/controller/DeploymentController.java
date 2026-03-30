package com.git2go.platform.controller;

import com.git2go.platform.dto.response.DeploymentResponse;
import com.git2go.platform.service.BuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Deployment Controller — ALL endpoints require authentication + authorization.
 */
@RestController
@RequiredArgsConstructor
public class DeploymentController {

    private final BuildService buildService;

    @PostMapping("/api/projects/{projectId}/deploy")
    public ResponseEntity<DeploymentResponse> deploy(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        DeploymentResponse response = buildService.triggerDeployment(projectId, userDetails.getUsername());
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @GetMapping("/api/projects/{projectId}/deployments")
    public ResponseEntity<List<DeploymentResponse>> getProjectDeployments(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<DeploymentResponse> deployments = buildService.getProjectDeployments(projectId, userDetails.getUsername());
        return ResponseEntity.ok(deployments);
    }

    @GetMapping("/api/deployments/{id}")
    public ResponseEntity<DeploymentResponse> getDeployment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        DeploymentResponse response = buildService.getDeployment(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // Logs moved to LogController — /api/deployments/{id}/logs/build, /logs/runtime

    @PostMapping("/api/deployments/{id}/stop")
    public ResponseEntity<Void> stopDeployment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        buildService.stopDeployment(id, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/deployments/{id}/restart")
    public ResponseEntity<Void> restartDeployment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        buildService.restartDeployment(id, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
