package com.git2go.platform.orchestrator;

import com.git2go.platform.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DockerOrchestrator implements ContainerOrchestrator {

    private static final long BUILD_TIMEOUT_MINUTES = 10;
    private static final long COMMAND_TIMEOUT_MINUTES = 2;

    @Override
    public String buildImage(BuildRequest request) {
        log.info("Building Docker image: {} from {}", request.getImageName(), request.getBuildDirectory());

        List<String> command = List.of(
                "docker", "build",
                "-t", request.getImageName(),
                "-f", request.getDockerfilePath(),
                request.getBuildDirectory()
        );

        CommandResult result = executeCommand(command, BUILD_TIMEOUT_MINUTES);

        if (!result.success()) {
            log.error("Docker build failed: {}", result.output());
            throw new ApiException("Docker build failed: " + result.output(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Docker image built successfully: {}", request.getImageName());
        return request.getImageName();
    }

    @Override
    public String runContainer(RunContainerRequest request) {
        log.info("Running container: {} from image: {}", request.getContainerName(), request.getImageName());

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("-d");
        command.add("--name");
        command.add(request.getContainerName());
        command.add("-p");
        command.add(request.getHostPort() + ":" + request.getContainerPort());
        command.add("--memory");
        command.add(request.getMemoryLimitMb() + "m");
        command.add("--cpus");
        command.add(String.valueOf(request.getCpuLimit()));
        command.add("--restart");
        command.add("on-failure:3");

        if (request.getEnvVariables() != null) {
            request.getEnvVariables().forEach((key, value) -> {
                command.add("-e");
                command.add(key + "=" + value);
            });
        }

        command.add(request.getImageName());

        CommandResult result = executeCommand(command, COMMAND_TIMEOUT_MINUTES);

        if (!result.success()) {
            log.error("Container run failed: {}", result.output());
            throw new ApiException("Failed to start container: " + result.output(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String containerId = result.output() != null ? result.output().trim() : "";
        if (containerId.isEmpty()) {
            throw new ApiException("Container started but no ID returned", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        log.info("Container started: {} (ID: {})", request.getContainerName(), containerId);
        return containerId;
    }

    @Override
    public void stopContainer(String containerId) {
        log.info("Stopping container: {}", containerId);
        CommandResult result = executeCommand(List.of("docker", "stop", containerId), COMMAND_TIMEOUT_MINUTES);
        if (!result.success()) {
            log.error("Failed to stop container: {}", result.output());
            throw new ApiException("Failed to stop container", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void restartContainer(String containerId) {
        log.info("Restarting container: {}", containerId);
        CommandResult result = executeCommand(List.of("docker", "restart", containerId), COMMAND_TIMEOUT_MINUTES);
        if (!result.success()) {
            log.error("Failed to restart container: {}", result.output());
            throw new ApiException("Failed to restart container", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void removeContainer(String containerId) {
        log.info("Removing container: {}", containerId);
        CommandResult result = executeCommand(List.of("docker", "rm", "-f", containerId), COMMAND_TIMEOUT_MINUTES);
        if (!result.success()) {
            // Log warning but don't throw — container might already be removed
            log.warn("Failed to remove container {}: {}", containerId, result.output());
        }
    }

    @Override
    public void removeImage(String imageId) {
        log.info("Removing image: {}", imageId);
        CommandResult result = executeCommand(List.of("docker", "rmi", "-f", imageId), COMMAND_TIMEOUT_MINUTES);
        if (!result.success()) {
            log.warn("Failed to remove image {}: {}", imageId, result.output());
        }
    }

    @Override
    public int getExposedPort(String imageId) {
        // docker inspect --format '{{json .Config.ExposedPorts}}' image
        // Returns: {"8080/tcp":{}} or {"3000/tcp":{}}
        CommandResult result = executeCommand(
                List.of("docker", "inspect", "--format", "{{json .Config.ExposedPorts}}", imageId),
                COMMAND_TIMEOUT_MINUTES
        );

        if (!result.success() || result.output().isBlank()) return 0;

        try {
            // Parse "{"8080/tcp":{}}" → extract 8080
            String output = result.output().trim();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"(\\d+)/tcp\"").matcher(output);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("Could not parse exposed port from image: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public ContainerStatus getContainerStatus(String containerId) {
        CommandResult result = executeCommand(
                List.of("docker", "inspect", "--format", "{{.State.Status}}", containerId),
                COMMAND_TIMEOUT_MINUTES
        );

        if (!result.success()) {
            return ContainerStatus.NOT_FOUND;
        }

        String status = result.output().trim().toLowerCase();
        return switch (status) {
            case "running" -> ContainerStatus.RUNNING;
            case "exited" -> ContainerStatus.EXITED;
            default -> ContainerStatus.STOPPED;
        };
    }

    @Override
    public String getContainerLogs(String containerId, int tailLines) {
        CommandResult result = executeCommand(
                List.of("docker", "logs", "--tail", String.valueOf(tailLines), containerId),
                COMMAND_TIMEOUT_MINUTES
        );
        return result.output();
    }

    @Override
    public ContainerStats getContainerStats(String containerId) {
        CommandResult result = executeCommand(
                List.of("docker", "stats", "--no-stream", "--format",
                        "{{.CPUPerc}}|{{.MemUsage}}|{{.NetIO}}", containerId),
                COMMAND_TIMEOUT_MINUTES
        );

        if (!result.success() || result.output().isBlank()) {
            return ContainerStats.builder().build();
        }

        return parseStats(result.output().trim());
    }

    // ==================== HELPERS ====================

    /**
     * Command execute with proper process cleanup.
     */
    private CommandResult executeCommand(List<String> command, long timeoutMinutes) {
        Process process = null;
        try {
            log.debug("Executing: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().put("DOCKER_BUILDKIT", "0");
            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);

            if (!completed) {
                process.destroyForcibly();
                return new CommandResult(false, "Command timed out after " + timeoutMinutes + " minutes");
            }

            int exitCode = process.exitValue();
            return new CommandResult(exitCode == 0, output);

        } catch (Exception e) {
            log.error("Command execution failed: {}", e.getMessage());
            return new CommandResult(false, "Command execution error: " + e.getMessage());
        } finally {
            // Process cleanup — resource leak prevent
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Safe stats parsing with bounds checking
     */
    private ContainerStats parseStats(String statsOutput) {
        try {
            String[] parts = statsOutput.split("\\|");
            if (parts.length < 2) {
                log.warn("Unexpected stats format: {}", statsOutput);
                return ContainerStats.builder().build();
            }

            double cpuPercent = Double.parseDouble(parts[0].replace("%", "").trim());

            String[] memParts = parts[1].split("/");
            if (memParts.length < 2) {
                return ContainerStats.builder().cpuUsagePercent(cpuPercent).build();
            }

            long memUsage = parseMemoryValue(memParts[0].trim());
            long memLimit = parseMemoryValue(memParts[1].trim());

            return ContainerStats.builder()
                    .cpuUsagePercent(cpuPercent)
                    .memoryUsageMb(memUsage)
                    .memoryLimitMb(memLimit)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse container stats: {}", statsOutput);
            return ContainerStats.builder().build();
        }
    }

    private long parseMemoryValue(String value) {
        value = value.trim();
        if (value.endsWith("GiB")) return (long) (Double.parseDouble(value.replace("GiB", "")) * 1024);
        if (value.endsWith("MiB")) return (long) Double.parseDouble(value.replace("MiB", ""));
        if (value.endsWith("KiB")) return (long) (Double.parseDouble(value.replace("KiB", "")) / 1024);
        return 0;
    }

    private record CommandResult(boolean success, String output) {}
}
