package com.git2go.platform.repository;

import com.git2go.platform.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // Paginated query — Spring automatically handles LIMIT, OFFSET, ORDER BY
    Page<Project> findByUserId(UUID userId, Pageable pageable);

    // Non-paginated — monitoring me saare projects chahiye
    List<Project> findByUserId(UUID userId);

    // Check project name already exists for this user (duplicate prevent)
    boolean existsByNameAndUserId(String name, UUID userId);

    // Count user's projects — efficient (SELECT COUNT vs loading all rows)
    long countByUserId(UUID userId);
}
