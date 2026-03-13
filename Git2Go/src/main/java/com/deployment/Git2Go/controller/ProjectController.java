package com.deployment.Git2Go.controller;

import com.deployment.Git2Go.dto.request.CreateProjectRequest;
import com.deployment.Git2Go.dto.response.ApiResponse;
import com.deployment.Git2Go.dto.response.ProjectResponse;
import com.deployment.Git2Go.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication) {

        ProjectResponse response = projectService.createProject(
            request, authentication.getName());

        return ResponseEntity.ok(
            ApiResponse.success("Project created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getMyProjects(
            Authentication authentication) {

        List<ProjectResponse> projects =
            projectService.getUserProjects(authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable String projectId,
            Authentication authentication) {

        ProjectResponse response = projectService.getProject(
            projectId, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable String projectId,
            Authentication authentication) {

        projectService.deleteProject(projectId, authentication.getName());

        return ResponseEntity.ok(
            ApiResponse.success("Project deleted", null));
    }
}