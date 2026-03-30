package com.git2go.platform.repository;

import com.git2go.platform.entity.BuildLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BuildLogRepository extends JpaRepository<BuildLog, UUID> {

    // Deployment ke saare logs — time order me
    List<BuildLog> findByDeploymentIdOrderByTimestampAsc(UUID deploymentId);
}
