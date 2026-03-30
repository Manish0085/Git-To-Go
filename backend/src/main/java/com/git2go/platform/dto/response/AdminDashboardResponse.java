package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalProjects;
    private long totalDeployments;
    private long runningDeployments;
    private long failedDeployments;
}
