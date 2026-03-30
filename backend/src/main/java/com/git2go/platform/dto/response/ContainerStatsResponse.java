package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class ContainerStatsResponse {
    private UUID deploymentId;
    private UUID projectId;
    private String projectName;
    private String containerStatus;  // RUNNING, STOPPED, EXITED
    private double cpuUsagePercent;
    private long memoryUsageMb;
    private long memoryLimitMb;
    private double memoryUsagePercent;
    private long networkInputBytes;
    private long networkOutputBytes;
    private String uptime;           // e.g., "3d 5h 12m"
    private LocalDateTime collectedAt;
}
