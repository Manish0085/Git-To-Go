package com.git2go.platform.dto.response;

import com.git2go.platform.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class ProjectResponse {
    private UUID id;
    private String name;
    private String repoUrl;
    private String branch;
    private Integer port;
    private ProjectStatus status;
    private boolean autoDeployEnabled;
    private String deployedUrl;
    private Map<String, String> envVariables;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
