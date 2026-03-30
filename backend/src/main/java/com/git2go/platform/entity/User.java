package com.git2go.platform.entity;

import com.git2go.platform.enums.AuthProvider;
import com.git2go.platform.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users") // "user" reserved keyword hai PostgreSQL me, isliye "users"
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // Password null ho sakta hai — kyunki Google/GitHub login me password nahi hota
    private String password;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider;

    // OAuth2 provider ka unique user ID (Google/GitHub ka internal ID)
    private String providerId;

    // GitHub specific — access token store karenge taaki baad me repos fetch kar sakein
    private String githubAccessToken;

    // Project limit — free tier max 5 projects per user
    @Column(nullable = false)
    @Builder.Default
    private int maxProjects = 5;

    // Email verification
    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    private String verificationToken;

    private LocalDateTime verificationTokenExpiry;

    // ==================== RELATIONSHIPS (Cascade delete) ====================

    // User delete → saare projects delete (projects → env vars, deployments bhi cascade)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Project> projects = new ArrayList<>();

    // User delete → saare refresh tokens delete
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
