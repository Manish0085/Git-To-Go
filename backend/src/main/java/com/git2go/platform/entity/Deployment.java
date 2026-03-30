package com.git2go.platform.entity;

import com.git2go.platform.enums.DeploymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deployments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Deployment version — har deploy pe increment
    private int version;

    // Docker image ID/name
    private String imageId;

    // Docker container ID
    private String containerId;

    // Container ka naam — unique hona chahiye
    private String containerName;

    // Host machine ka port (e.g., 3001, 3002)
    private int hostPort;

    // Deployed URL (e.g., myapp-abc123.yourdomain.com)
    private String deployedUrl;

    // Current deployment status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    // Failure message (agar fail hua toh reason yahan)
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    // Kis project ka deployment hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
