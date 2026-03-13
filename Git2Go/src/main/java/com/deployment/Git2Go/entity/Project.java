package com.deployment.Git2Go.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String repoUrl;

    private String branch;           // e.g. "main"

    private String webhookSecret;    // used to verify GitHub webhook calls

    // ⭐ These 3 fields power auto-rollback
    private String lastStableTaskDefArn;   // previous ECS task def ARN
    private String lastStableImageUri;     // previous ECR image URI
    private String ecsServiceName;         // ECS service name for this project

    private String appUrl;           // public URL after first deploy

    private String detectedTechStack; // e.g. SPRING_BOOT, NODEJS, PYTHON

    @OneToMany(mappedBy = "projectId", cascade = CascadeType.ALL)
    private List<Deployment> deployments;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}