package com.deployment.Git2Go.repository;

import com.deployment.Git2Go.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByUserId(String userId);
    Optional<Project> findByRepoUrlAndBranch(String repoUrl, String branch);
    boolean existsByUserIdAndName(String userId, String name);
}