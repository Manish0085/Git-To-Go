package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private UUID id;
    private String action;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String result;
    private String details;
    private String ipAddress;
    private LocalDateTime timestamp;
}
