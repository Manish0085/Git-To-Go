package com.git2go.platform.entity;

import com.git2go.platform.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    // GitHub repo URL — e.g., https://github.com/vidit/my-app.git
    @Column(nullable = false)
    private String repoUrl;

    // Branch — default "main", user change kar sakta hai
    @Column(nullable = false)
    private String branch;

    // Port jis pe app listen karta hai container ke andar
    @Column(nullable = false)
    private Integer port;

    // Project ka current status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    // Auto deploy on/off — GitHub webhook ke liye
    @Column(nullable = false)
    private boolean autoDeployEnabled;

    // Webhook secret — GitHub signature verify karne ke liye
    private String webhookSecret;

    // Deployed URL — e.g., myapp-abc123.yourdomain.com (null jab tak deploy nahi hua)
    private String deployedUrl;

    // ==================== RELATIONSHIPS ====================

    // Many Projects belong to One User
    // LAZY = User tab load hoga jab explicitly access karein (performance)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // One Project has Many Environment Variables
    // CascadeType.ALL = Project delete karo toh env vars bhi delete ho jayein
    // orphanRemoval = agar env var list se remove karo toh DB se bhi delete ho
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EnvVariable> envVariables = new ArrayList<>();

    // Project delete → saare deployments delete
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Deployment> deployments = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
