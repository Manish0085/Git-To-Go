package com.deployment.Git2Go.service;

import com.deployment.Git2Go.dto.request.DeploymentRequest;
import com.deployment.Git2Go.dto.response.DeploymentResponse;

import java.util.List;

public interface DeploymentService {
    DeploymentResponse triggerDeployment(DeploymentRequest request,
                                          String userEmail);
    DeploymentResponse getDeployment(String deploymentId);
    List<DeploymentResponse> getProjectDeployments(String projectId,
                                                    String userEmail);
    void rollback(String deploymentId, String userEmail);
}