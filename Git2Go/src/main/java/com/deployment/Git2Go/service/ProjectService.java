package com.deployment.Git2Go.service;

import com.deployment.Git2Go.dto.request.CreateProjectRequest;
import com.deployment.Git2Go.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectService {
    ProjectResponse createProject(CreateProjectRequest request, String userEmail);
    List<ProjectResponse> getUserProjects(String userEmail);
    ProjectResponse getProject(String projectId, String userEmail);
    void deleteProject(String projectId, String userEmail);
}