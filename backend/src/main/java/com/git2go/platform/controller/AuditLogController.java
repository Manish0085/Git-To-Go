package com.git2go.platform.controller;

import com.git2go.platform.dto.response.AuditLogResponse;
import com.git2go.platform.dto.response.PagedResponse;
import com.git2go.platform.service.AuditLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * GET /api/audit/activity           → User ki activity feed (paginated)
 * GET /api/audit/resource/{type}/{id} → Specific resource ki history
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Validated
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/activity")
    public ResponseEntity<PagedResponse<AuditLogResponse>> getUserActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        PagedResponse<AuditLogResponse> activity = auditLogService
                .getUserActivity(userDetails.getUsername(), page, size);
        return ResponseEntity.ok(activity);
    }

    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<PagedResponse<AuditLogResponse>> getResourceActivity(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        PagedResponse<AuditLogResponse> activity = auditLogService
                .getResourceActivity(resourceType, resourceId, page, size);
        return ResponseEntity.ok(activity);
    }
}
