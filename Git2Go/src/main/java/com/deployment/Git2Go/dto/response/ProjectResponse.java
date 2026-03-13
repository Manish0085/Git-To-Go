package com.deployment.Git2Go.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private String repoUrl;
    private String branch;
    private String appUrl;
    private String detectedTechStack;
    private String webhookUrl;
    private boolean webhookRegistered;
    private LocalDateTime createdAt;
}