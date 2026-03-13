package com.deployment.Git2Go.service.impl;

import com.deployment.Git2Go.dto.request.CreateProjectRequest;
import com.deployment.Git2Go.dto.response.ProjectResponse;
import com.deployment.Git2Go.entity.Project;
import com.deployment.Git2Go.entity.User;
import com.deployment.Git2Go.repository.ProjectRepository;
import com.deployment.Git2Go.repository.UserRepository;
import com.deployment.Git2Go.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final GitHubWebhookService gitHubWebhookService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public ProjectResponse createProject(CreateProjectRequest request,
                                         String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (projectRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new RuntimeException("Project with this name already exists");
        }

        // Generate webhook secret
        String webhookSecret = UUID.randomUUID()
            .toString().replace("-", "");

        Project project = Project.builder()
            .userId(user.getId())
            .name(request.getName())
            .repoUrl(request.getRepoUrl())
            .branch(request.getBranch() != null
                ? request.getBranch() : "main")
            .webhookSecret(webhookSecret)
            .build();

        projectRepository.save(project);

        // ⭐ Auto-register webhook on GitHub
        boolean webhookRegistered = false;
        if (user.getGithubToken() != null) {
            webhookRegistered = gitHubWebhookService.registerWebhook(
                request.getRepoUrl(),
                project.getId(),
                webhookSecret,
                user.getGithubToken()
            );
        }

        log.info("Project created: {} webhook={}",
            project.getName(), webhookRegistered);

        return toResponse(project, webhookRegistered);
    }

    @Override
    public List<ProjectResponse> getUserProjects(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return projectRepository.findByUserId(user.getId())
            .stream()
            .map(p -> toResponse(p, false))
            .collect(Collectors.toList());
    }

    @Override
    public ProjectResponse getProject(String projectId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUserId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return toResponse(project, false);
    }

    @Override
    public void deleteProject(String projectId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUserId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        projectRepository.delete(project);
        log.info("Project deleted: {}", projectId);
    }

    private ProjectResponse toResponse(Project project,
                                        boolean webhookRegistered) {
        return ProjectResponse.builder()
            .id(project.getId())
            .name(project.getName())
            .repoUrl(project.getRepoUrl())
            .branch(project.getBranch())
            .appUrl(project.getAppUrl())
            .detectedTechStack(project.getDetectedTechStack())
            .webhookUrl(appBaseUrl + "/api/webhook/github/" + project.getId())
            .webhookRegistered(webhookRegistered)
            .createdAt(project.getCreatedAt())
            .build();
    }
}