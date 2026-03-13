package com.deployment.Git2Go.dto.response;

import com.deployment.Git2Go.enums.BuildStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {
    private String id;
    private String projectId;
    private String projectName;
    private String repoUrl;
    private String branch;
    private String commitSha;
    private String commitMessage;
    private String detectedTechStack;
    private BuildStatus buildStatus;
    private String appUrl;
    private String triggeredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}