package com.git2go.platform.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ContainerStats {
    private double cpuUsagePercent;
    private long memoryUsageMb;
    private long memoryLimitMb;
    private long networkInputBytes;
    private long networkOutputBytes;
}
