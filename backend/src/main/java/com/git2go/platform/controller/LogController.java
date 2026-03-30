package com.git2go.platform.controller;

import com.git2go.platform.dto.response.BuildLogResponse;
import com.git2go.platform.service.LogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/logs")
@RequiredArgsConstructor
@Validated
public class LogController {

    private final LogService logService;

    @GetMapping("/build")
    public ResponseEntity<List<BuildLogResponse>> getBuildLogs(
            @PathVariable UUID deploymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<BuildLogResponse> logs = logService.getBuildLogs(deploymentId, userDetails.getUsername());
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/build/tail")
    public ResponseEntity<List<BuildLogResponse>> getBuildLogsTail(
            @PathVariable UUID deploymentId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int lines,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<BuildLogResponse> logs = logService.getBuildLogsTail(deploymentId, userDetails.getUsername(), lines);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/runtime")
    public ResponseEntity<List<String>> getRuntimeLogs(
            @PathVariable UUID deploymentId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int lines,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<String> logs = logService.getRuntimeLogs(deploymentId, userDetails.getUsername(), lines);
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/stream/start")
    public ResponseEntity<Void> startLogStream(
            @PathVariable UUID deploymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        logService.startRuntimeLogStream(deploymentId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stream/stop")
    public ResponseEntity<Void> stopLogStream(
            @PathVariable UUID deploymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        logService.stopRuntimeLogStream(deploymentId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
