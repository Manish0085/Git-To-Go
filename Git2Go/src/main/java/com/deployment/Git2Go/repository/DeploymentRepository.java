package com.deployment.Git2Go.repository;

import com.deployment.Git2Go.entity.Deployment;
import com.deployment.Git2Go.enums.BuildStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, String> {
    List<Deployment> findByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<Deployment> findTopByProjectIdAndBuildStatusOrderByCreatedAtDesc(
        String projectId, BuildStatus status
    );
    List<Deployment> findByProjectIdAndBuildStatus(String projectId, BuildStatus status);
}