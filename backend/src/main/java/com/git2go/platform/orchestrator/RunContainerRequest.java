package com.git2go.platform.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Container run ke liye input — kaunsi image, kaunsa port, kitni memory
 */
@Getter
@AllArgsConstructor
@Builder
public class RunContainerRequest {
    private String imageName;
    private String containerName;
    private int hostPort;          // host machine ka port (exposed to outside)
    private int containerPort;     // container ke andar app kis port pe listen karta hai
    private Map<String, String> envVariables;
    private long memoryLimitMb;    // max memory in MB (e.g., 512)
    private double cpuLimit;       // max CPU cores (e.g., 0.5)
}
