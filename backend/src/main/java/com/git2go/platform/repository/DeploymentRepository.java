package com.git2go.platform.repository;

import com.git2go.platform.entity.Deployment;
import com.git2go.platform.enums.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<Deployment> findFirstByProjectIdOrderByCreatedAtDesc(UUID projectId);

    int countByProjectId(UUID projectId);

    // Health check ke liye — sirf RUNNING deployments with container
    List<Deployment> findByStatus(DeploymentStatus status);
}
