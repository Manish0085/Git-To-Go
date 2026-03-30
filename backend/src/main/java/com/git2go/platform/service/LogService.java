package com.git2go.platform.service;

import com.git2go.platform.dto.response.BuildLogResponse;
import com.git2go.platform.entity.Deployment;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.logging.LogEntry;
import com.git2go.platform.logging.LogStorageService;
import com.git2go.platform.orchestrator.ContainerOrchestrator;
import com.git2go.platform.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Log Service — Build logs read + Runtime logs stream via WebSocket.
 *
 * Key design decisions:
 * - No @Transactional on @Async — eagerly load data before async call
 * - ConcurrentHashMap.putIfAbsent() — atomic check-and-set, no race condition
 * - Process timeout — docker logs won't run forever
 * - WebSocket message truncation — prevent oversized frames
 *
 * Interview: "Build logs file system se read hote hain — streaming API se, pura file
 * memory me nahi. Runtime logs docker logs -f se stream hote hain WebSocket pe.
 * Active streams ConcurrentHashMap me track hote hain — putIfAbsent se atomic
 * check-and-set, race condition nahi. Process pe timeout hai — 2 hours max,
 * uske baad auto-stop. WebSocket messages 8KB pe truncate hote hain."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final LogStorageService logStorageService;
    private final ContainerOrchestrator containerOrchestrator;
    private final DeploymentRepository deploymentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Track active streams — deploymentId → true (active) / false (stopping)
    private final Map<UUID, Boolean> activeStreams = new ConcurrentHashMap<>();

    private static final long STREAM_TIMEOUT_HOURS = 2;
    private static final int MAX_WS_MESSAGE_LENGTH = 8192; // 8KB per WebSocket message

    // ==================== BUILD LOGS (File System) ====================

    @Transactional(readOnly = true)
    public List<BuildLogResponse> getBuildLogs(UUID deploymentId, String userEmail) {
        validateAccess(deploymentId, userEmail);

        return logStorageService.readLogs(deploymentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BuildLogResponse> getBuildLogsTail(UUID deploymentId, String userEmail, int lines) {
        validateAccess(deploymentId, userEmail);

        return logStorageService.readLastNLines(deploymentId, lines).stream()
                .map(this::toResponse)
                .toList();
    }

    // ==================== RUNTIME LOGS (Docker + WebSocket) ====================

    /**
     * Start real-time log stream.
     *
     * NOT @Transactional — eagerly load containerId before going async.
     * putIfAbsent for atomic duplicate prevention.
     * Process has 2-hour timeout — won't run forever.
     */
    public void startRuntimeLogStream(UUID deploymentId, String userEmail) {
        // Validate + eagerly load containerId BEFORE async call (avoid lazy loading)
        String containerId = getContainerIdWithAuth(deploymentId, userEmail);

        if (containerId == null) {
            throw new ApiException("No running container for this deployment", HttpStatus.BAD_REQUEST);
        }

        // Atomic check-and-set — no race condition
        Boolean existing = activeStreams.putIfAbsent(deploymentId, true);
        if (existing != null) {
            log.debug("Log stream already active for deployment: {}", deploymentId);
            return;
        }

        // Start async stream with plain data (no entities)
        executeLogStream(deploymentId, containerId);
    }

    @Async("buildExecutor")
    public void executeLogStream(UUID deploymentId, String containerId) {
        String topic = "/topic/logs/" + deploymentId;
        log.info("Starting runtime log stream for deployment: {}", deploymentId);

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "logs", "-f", "--tail", "100", containerId
            );
            pb.redirectErrorStream(true);
            process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                long startTime = System.currentTimeMillis();
                long timeoutMs = STREAM_TIMEOUT_HOURS * 3600 * 1000;

                while ((line = reader.readLine()) != null
                        && Boolean.TRUE.equals(activeStreams.get(deploymentId))
                        && (System.currentTimeMillis() - startTime) < timeoutMs) {

                    // Truncate oversized log lines — prevent WebSocket frame overflow
                    String safeLine = line.length() > MAX_WS_MESSAGE_LENGTH
                            ? line.substring(0, MAX_WS_MESSAGE_LENGTH) + "... [truncated]"
                            : line;

                    BuildLogResponse logMessage = BuildLogResponse.builder()
                            .message(safeLine)
                            .level("INFO")
                            .timestamp(java.time.LocalDateTime.now())
                            .build();

                    try {
                        messagingTemplate.convertAndSend(topic, logMessage);
                    } catch (Exception e) {
                        // WebSocket send failed — client probably disconnected
                        log.debug("WebSocket send failed, stopping stream: {}", e.getMessage());
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Runtime log streaming failed for deployment {}: {}", deploymentId, e.getMessage());
        } finally {
            activeStreams.remove(deploymentId);
            if (process != null) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS); // Wait for cleanup
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Runtime log stream stopped for deployment: {}", deploymentId);
        }
    }

    /**
     * Stop log stream — requires auth
     */
    public void stopRuntimeLogStream(UUID deploymentId, String userEmail) {
        // Validate access
        getContainerIdWithAuth(deploymentId, userEmail);

        if (activeStreams.containsKey(deploymentId)) {
            activeStreams.put(deploymentId, false);
            log.info("Stopping runtime log stream for deployment: {}", deploymentId);
        }
    }

    /**
     * One-shot runtime logs — last N lines, no streaming
     */
    @Transactional(readOnly = true)
    public List<String> getRuntimeLogs(UUID deploymentId, String userEmail, int tailLines) {
        String containerId = getContainerIdWithAuth(deploymentId, userEmail);

        if (containerId == null) {
            return List.of("No running container for this deployment.");
        }

        String logs = containerOrchestrator.getContainerLogs(containerId, tailLines);

        if (logs == null || logs.isBlank()) {
            return List.of();
        }

        // Filter out empty lines
        return List.of(logs.split("\n")).stream()
                .filter(line -> !line.isBlank())
                .toList();
    }

    // ==================== HELPERS ====================

    /**
     * Validate access + eagerly load containerId.
     * Used BEFORE @Async calls to avoid lazy loading issues.
     */
    @Transactional(readOnly = true)
    public String getContainerIdWithAuth(UUID deploymentId, String userEmail) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ApiException("Deployment not found", HttpStatus.NOT_FOUND));

        if (!deployment.getProject().getUser().getEmail().equals(userEmail)) {
            throw new ApiException("You don't have access to this deployment", HttpStatus.FORBIDDEN);
        }

        return deployment.getContainerId();
    }

    private void validateAccess(UUID deploymentId, String userEmail) {
        getContainerIdWithAuth(deploymentId, userEmail);
    }

    private BuildLogResponse toResponse(LogEntry entry) {
        return BuildLogResponse.builder()
                .message(entry.getMessage())
                .level(entry.getLevel())
                .timestamp(entry.getTimestamp())
                .build();
    }
}
