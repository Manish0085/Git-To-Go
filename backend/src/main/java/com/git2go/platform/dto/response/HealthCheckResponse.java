package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class HealthCheckResponse {
    private UUID deploymentId;
    private String containerStatus;
    private boolean healthy;
    private String message;
    private int httpStatusCode;      // 0 if unreachable
    private long responseTimeMs;
    private LocalDateTime checkedAt;
}
