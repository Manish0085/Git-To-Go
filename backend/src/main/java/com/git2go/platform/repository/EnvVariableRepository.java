package com.git2go.platform.repository;

import com.git2go.platform.entity.EnvVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvVariableRepository extends JpaRepository<EnvVariable, UUID> {

    // Project ke saare env variables
    List<EnvVariable> findByProjectId(UUID projectId);

    // Project ke saare env vars delete — update ke time purane hatake naye daalenge
    void deleteByProjectId(UUID projectId);
}
