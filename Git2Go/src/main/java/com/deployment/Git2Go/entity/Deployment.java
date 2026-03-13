package com.deployment.Git2Go.entity;

import com.deployment.Git2Go.enums.BuildStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "deployments")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String projectId;

    private String repoUrl;
    private String branch;
    private String commitSha;
    private String commitMessage;

    private String detectedTechStack;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildStatus buildStatus = BuildStatus.QUEUED;

    private String imageUri;            // ECR image URI after build

    private String ecsTaskDefArn;       // new task def ARN
    private String previousTaskDefArn;  // ⭐ snapshot for rollback

    private String appUrl;

    // "manual" or "webhook"
    private String triggeredBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}