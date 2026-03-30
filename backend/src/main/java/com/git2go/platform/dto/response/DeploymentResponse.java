package com.git2go.platform.dto.response;

import com.git2go.platform.enums.DeploymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class DeploymentResponse {
    private UUID id;
    private UUID projectId;
    private int version;
    private DeploymentStatus status;
    private String deployedUrl;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
