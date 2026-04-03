package com.git2go.platform.service;

import com.git2go.platform.audit.Auditable;
import com.git2go.platform.dto.request.CreateProjectRequest;
import com.git2go.platform.dto.request.UpdateProjectRequest;
import com.git2go.platform.dto.response.PagedResponse;
import com.git2go.platform.dto.response.ProjectResponse;
import com.git2go.platform.entity.EnvVariable;
import com.git2go.platform.entity.Project;
import com.git2go.platform.entity.User;
import com.git2go.platform.enums.ProjectStatus;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.orchestrator.ContainerOrchestrator;
import com.git2go.platform.repository.ProjectRepository;
import com.git2go.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ContainerOrchestrator containerOrchestrator;

    @Transactional
    @Auditable(action = "CREATE_PROJECT")
    public ProjectResponse createProject(CreateProjectRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        // Project limit check — free tier max 5
        long currentCount = projectRepository.countByUserId(user.getId());
        if (currentCount >= user.getMaxProjects()) {
            throw new ApiException(
                    "Project limit reached. Maximum " + user.getMaxProjects() + " projects allowed.",
                    HttpStatus.FORBIDDEN);
        }

        if (projectRepository.existsByNameAndUserId(request.getName(), user.getId())) {
            throw new ApiException("Project with this name already exists", HttpStatus.CONFLICT);
        }

        Project project = Project.builder()
                .name(request.getName())
                .repoUrl(request.getRepoUrl())
                .branch(request.getBranch())
                .port(request.getPort() != null ? request.getPort() : 0) // 0 = auto-detect during build
                .status(ProjectStatus.CREATED)
                .autoDeployEnabled(false)
                .user(user)
                .build();

        if (request.getEnvVariables() != null && !request.getEnvVariables().isEmpty()) {
            request.getEnvVariables().forEach((key, value) -> {
                EnvVariable envVar = EnvVariable.builder()
                        .key(key)
                        .value(value)
                        .project(project)
                        .build();
                project.getEnvVariables().add(envVar);
            });
        }

        Project savedProject = projectRepository.save(project);
        log.info("Project created: {} by user: {}", savedProject.getName(), userEmail);
        return mapToResponse(savedProject);
    }

    /**
     * Paginated project listing
     *
     * Interview: "Pagination use ki hai Spring Data Pageable se — client page number
     * aur size bhejta hai, Spring internally LIMIT aur OFFSET query generate karta hai.
     * Total elements aur total pages bhi return karte hain taaki frontend pagination
     * UI bana sake."
     */
    @Transactional(readOnly = true) // readOnly = Hibernate dirty checking skip karega — performance boost
    public PagedResponse<ProjectResponse> getUserProjects(String userEmail, int page, int size) {
        User user = getUserByEmail(userEmail);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> projectPage = projectRepository.findByUserId(user.getId(), pageable);

        return PagedResponse.<ProjectResponse>builder()
                .content(projectPage.getContent().stream().map(this::mapToResponse).toList())
                .page(projectPage.getNumber())
                .size(projectPage.getSize())
                .totalElements(projectPage.getTotalElements())
                .totalPages(projectPage.getTotalPages())
                .last(projectPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, String userEmail) {
        Project project = getProjectAndValidateOwner(projectId, userEmail);
        return mapToResponse(project);
    }

    @Transactional
    @Auditable(action = "UPDATE_PROJECT")
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, String userEmail) {
        Project project = getProjectAndValidateOwner(projectId, userEmail);

        if (request.getName() != null) project.setName(request.getName());
        if (request.getBranch() != null) project.setBranch(request.getBranch());
        if (request.getPort() != null) project.setPort(request.getPort());
        if (request.getAutoDeployEnabled() != null) project.setAutoDeployEnabled(request.getAutoDeployEnabled());

        if (request.getEnvVariables() != null) {
            project.getEnvVariables().clear();
            request.getEnvVariables().forEach((key, value) -> {
                EnvVariable envVar = EnvVariable.builder()
                        .key(key)
                        .value(value)
                        .project(project)
                        .build();
                project.getEnvVariables().add(envVar);
            });
        }

        Project updatedProject = projectRepository.save(project);
        log.info("Project updated: {} by user: {}", updatedProject.getName(), userEmail);
        return mapToResponse(updatedProject);
    }

    @Transactional
    @Auditable(action = "DELETE_PROJECT")
    public void deleteProject(UUID projectId, String userEmail) {
        Project project = getProjectAndValidateOwner(projectId, userEmail);

        // Stop and remove all Docker containers + images before DB delete
        for (var deployment : project.getDeployments()) {
            if (deployment.getContainerId() != null) {
                try {
                    containerOrchestrator.stopContainer(deployment.getContainerId());
                    containerOrchestrator.removeContainer(deployment.getContainerId());
                    log.info("Stopped and removed container: {}", deployment.getContainerId());
                } catch (Exception e) {
                    log.warn("Failed to cleanup container {}: {}", deployment.getContainerId(), e.getMessage());
                }
            }
            if (deployment.getImageId() != null) {
                try {
                    containerOrchestrator.removeImage(deployment.getImageId());
                    log.info("Removed image: {}", deployment.getImageId());
                } catch (Exception e) {
                    log.warn("Failed to remove image {}: {}", deployment.getImageId(), e.getMessage());
                }
            }
        }

        projectRepository.delete(project);
        log.info("Project deleted: {} by user: {}", project.getName(), userEmail);
    }

    // ==================== HELPER METHODS ====================

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    private Project getProjectAndValidateOwner(UUID projectId, String userEmail) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException("Project not found", HttpStatus.NOT_FOUND));

        if (!project.getUser().getEmail().equals(userEmail)) {
            // Log suspicious access attempt — security monitoring ke liye important
            log.warn("Unauthorized project access attempt: user={} tried to access project={}", userEmail, projectId);
            throw new ApiException("You don't have access to this project", HttpStatus.FORBIDDEN);
        }

        return project;
    }

    private ProjectResponse mapToResponse(Project project) {
        Map<String, String> envVars = project.getEnvVariables().stream()
                .collect(Collectors.toMap(EnvVariable::getKey, EnvVariable::getValue));

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .repoUrl(project.getRepoUrl())
                .branch(project.getBranch())
                .port(project.getPort())
                .status(project.getStatus())
                .autoDeployEnabled(project.isAutoDeployEnabled())
                .deployedUrl(project.getDeployedUrl())
                .envVariables(envVars)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
