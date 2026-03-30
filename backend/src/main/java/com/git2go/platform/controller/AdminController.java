package com.git2go.platform.controller;

import com.git2go.platform.dto.response.*;
import com.git2go.platform.enums.Role;
import com.git2go.platform.service.AdminService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin Controller — ADMIN role only endpoints.
 *
 * All methods in AdminService have @PreAuthorize("hasRole('ADMIN')").
 * If a USER role tries to access → 403 Forbidden.
 *
 * GET    /api/admin/dashboard             → Overview stats
 * GET    /api/admin/users                 → All users (paginated)
 * PUT    /api/admin/users/{id}/role       → Change user role
 * DELETE /api/admin/users/{id}            → Delete user + all resources
 * GET    /api/admin/projects              → All projects (paginated)
 * POST   /api/admin/deployments/{id}/stop → Force stop any deployment
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/users")
    public ResponseEntity<PagedResponse<AdminUserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.getAllUsers(page, size));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<AdminUserResponse> changeUserRole(
            @PathVariable UUID userId,
            @RequestParam Role role) {
        return ResponseEntity.ok(adminService.changeUserRole(userId, role));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects")
    public ResponseEntity<PagedResponse<ProjectResponse>> getAllProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.getAllProjects(page, size));
    }

    @PostMapping("/deployments/{deploymentId}/stop")
    public ResponseEntity<Void> forceStopDeployment(@PathVariable UUID deploymentId) {
        adminService.forceStopDeployment(deploymentId);
        return ResponseEntity.ok().build();
    }
}
