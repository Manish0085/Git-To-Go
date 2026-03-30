package com.git2go.platform.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Docker build ke liye input — kahan se build karna hai, image ka naam kya rakhna hai
 */
@Getter
@AllArgsConstructor
@Builder
public class BuildRequest {
    private String buildDirectory;  // jahan source code + Dockerfile hai
    private String imageName;       // e.g., "git2go/myapp:v1"
    private String dockerfilePath;  // Dockerfile ka path (relative to buildDir)
}
