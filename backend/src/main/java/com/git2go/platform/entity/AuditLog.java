package com.git2go.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        // Index on userEmail + timestamp — user ki activity fast query hogi
        @Index(name = "idx_audit_user_time", columnList = "userEmail, timestamp DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Kya action hua — CREATE_PROJECT, DEPLOY, LOGIN, etc.
    @Column(nullable = false)
    private String action;

    // Kisne kiya
    @Column(nullable = false)
    private String userEmail;

    // Kis resource pe kiya (project name, deployment ID, etc.)
    private String resourceType;   // "PROJECT", "DEPLOYMENT", "AUTH"

    private String resourceId;     // UUID of the resource

    private String resourceName;   // Human-readable name (project name, etc.)

    // Result — SUCCESS ya FAILURE
    @Column(nullable = false)
    private String result;

    // Extra details — error message (if failed), or key info
    @Column(columnDefinition = "TEXT")
    private String details;

    // IP address — security audit ke liye
    private String ipAddress;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
