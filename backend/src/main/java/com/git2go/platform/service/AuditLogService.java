package com.git2go.platform.service;

import com.git2go.platform.dto.response.AuditLogResponse;
import com.git2go.platform.dto.response.PagedResponse;
import com.git2go.platform.entity.AuditLog;
import com.git2go.platform.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> getUserActivity(String userEmail, int page, int size) {
        Page<AuditLog> auditPage = auditLogRepository
                .findByUserEmailOrderByTimestampDesc(userEmail, PageRequest.of(page, size));

        return PagedResponse.<AuditLogResponse>builder()
                .content(auditPage.getContent().stream().map(this::toResponse).toList())
                .page(auditPage.getNumber())
                .size(auditPage.getSize())
                .totalElements(auditPage.getTotalElements())
                .totalPages(auditPage.getTotalPages())
                .last(auditPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> getResourceActivity(String resourceType, String resourceId,
                                                                 int page, int size) {
        Page<AuditLog> auditPage = auditLogRepository
                .findByResourceTypeAndResourceIdOrderByTimestampDesc(
                        resourceType, resourceId, PageRequest.of(page, size));

        return PagedResponse.<AuditLogResponse>builder()
                .content(auditPage.getContent().stream().map(this::toResponse).toList())
                .page(auditPage.getNumber())
                .size(auditPage.getSize())
                .totalElements(auditPage.getTotalElements())
                .totalPages(auditPage.getTotalPages())
                .last(auditPage.isLast())
                .build();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .resourceName(log.getResourceName())
                .result(log.getResult())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .build();
    }
}
