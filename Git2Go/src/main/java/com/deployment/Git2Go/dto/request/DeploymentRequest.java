package com.deployment.Git2Go.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeploymentRequest {

    @NotBlank(message = "Project ID is required")
    private String projectId;

    // Optional — if null uses project's default branch
    private String branch;

    // Optional — for display purposes
    private String commitMessage;
}