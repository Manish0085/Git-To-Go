package com.git2go.platform.service;

import com.git2go.platform.audit.Auditable;
import com.git2go.platform.dto.response.*;
import com.git2go.platform.entity.Deployment;
import com.git2go.platform.entity.Project;
import com.git2go.platform.entity.User;
import com.git2go.platform.enums.DeploymentStatus;
import com.git2go.platform.enums.Role;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.orchestrator.ContainerOrchestrator;
import com.git2go.platform.repository.DeploymentRepository;
import com.git2go.platform.repository.ProjectRepository;
import com.git2go.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin Service — platform-wide management operations.
 *
 * @PreAuthorize("hasRole('ADMIN')") — sirf ADMIN role wale users access kar sakte hain.
 * Spring Security method-level authorization — agar koi USER role se call kare toh 403.
 *
 * Interview: "RBAC implement kiya hai Spring Security ke @PreAuthorize annotation se.
 * Method-level authorization hai — SecurityFilterChain me URL-level rules hain,
 * @PreAuthorize se fine-grained method-level control. hasRole('ADMIN') internally
 * ROLE_ADMIN GrantedAuthority check karta hai jo CustomUserDetailsService me set kiya hai."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final ContainerOrchestrator containerOrchestrator;

    // ==================== DASHBOARD OVERVIEW ====================

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.count();
        long totalDeployments = deploymentRepository.count();
        long runningDeployments = deploymentRepository.findByStatus(DeploymentStatus.RUNNING).size();
        long failedDeployments = deploymentRepository.findByStatus(DeploymentStatus.FAILED).size();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalProjects(totalProjects)
                .totalDeployments(totalDeployments)
                .runningDeployments(runningDeployments)
                .failedDeployments(failedDeployments)
                .build();
    }

    // ==================== USER MANAGEMENT ====================

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> getAllUsers(int page, int size) {
        Page<User> userPage = userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminUserResponse> users = userPage.getContent().stream()
                .map(user -> {
                    int projectCount = projectRepository.findByUserId(user.getId()).size();
                    return AdminUserResponse.builder()
                            .id(user.getId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .avatarUrl(user.getAvatarUrl())
                            .role(user.getRole())
                            .authProvider(user.getAuthProvider())
                            .emailVerified(user.isEmailVerified())
                            .projectCount(projectCount)
                            .createdAt(user.getCreatedAt())
                            .build();
                })
                .toList();

        return PagedResponse.<AdminUserResponse>builder()
                .content(users)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ADMIN_CHANGE_USER_ROLE")
    @Transactional
    public AdminUserResponse changeUserRole(UUID userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        user.setRole(newRole);
        userRepository.save(user);

        log.info("Admin changed role for user {} to {}", user.getEmail(), newRole);

        return AdminUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ADMIN_DELETE_USER")
    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        // Admin khud ko delete nahi kar sakta
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException("Cannot delete admin user", HttpStatus.FORBIDDEN);
        }

        log.info("Admin deleting user: {} and all their resources", user.getEmail());

        // User ke saare projects ki deployments stop karo
        List<Project> userProjects = projectRepository.findByUserId(userId);
        for (Project project : userProjects) {
            stopProjectDeployments(project.getId());
        }

        // Cascade delete hoga — user → projects → env vars, deployments
        userRepository.delete(user);
    }

    // ==================== PROJECT MANAGEMENT ====================

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PagedResponse<ProjectResponse> getAllProjects(int page, int size) {
        Page<Project> projectPage = projectRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<ProjectResponse> projects = projectPage.getContent().stream()
                .map(project -> ProjectResponse.builder()
                        .id(project.getId())
                        .name(project.getName())
                        .repoUrl(project.getRepoUrl())
                        .branch(project.getBranch())
                        .port(project.getPort())
                        .status(project.getStatus())
                        .autoDeployEnabled(project.isAutoDeployEnabled())
                        .deployedUrl(project.getDeployedUrl())
                        .createdAt(project.getCreatedAt())
                        .updatedAt(project.getUpdatedAt())
                        .build())
                .toList();

        return PagedResponse.<ProjectResponse>builder()
                .content(projects)
                .page(projectPage.getNumber())
                .size(projectPage.getSize())
                .totalElements(projectPage.getTotalElements())
                .totalPages(projectPage.getTotalPages())
                .last(projectPage.isLast())
                .build();
    }

    // ==================== DEPLOYMENT MANAGEMENT ====================

    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ADMIN_FORCE_STOP_DEPLOYMENT")
    @Transactional
    public void forceStopDeployment(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ApiException("Deployment not found", HttpStatus.NOT_FOUND));

        if (deployment.getContainerId() != null) {
            try {
                containerOrchestrator.stopContainer(deployment.getContainerId());
                containerOrchestrator.removeContainer(deployment.getContainerId());
            } catch (Exception e) {
                log.warn("Failed to stop container {}: {}", deployment.getContainerId(), e.getMessage());
            }
        }

        deployment.setStatus(DeploymentStatus.STOPPED);
        deployment.setFailureReason("Force stopped by admin");
        deploymentRepository.save(deployment);

        log.info("Admin force-stopped deployment: {}", deploymentId);
    }

    // ==================== HELPERS ====================

    private void stopProjectDeployments(UUID projectId) {
        deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(d -> d.getContainerId() != null && d.getStatus() == DeploymentStatus.RUNNING)
                .forEach(deployment -> {
                    try {
                        containerOrchestrator.stopContainer(deployment.getContainerId());
                        containerOrchestrator.removeContainer(deployment.getContainerId());
                        deployment.setStatus(DeploymentStatus.STOPPED);
                        deploymentRepository.save(deployment);
                    } catch (Exception e) {
                        log.warn("Failed to stop deployment {}: {}", deployment.getId(), e.getMessage());
                    }
                });
    }
}
