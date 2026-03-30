package com.git2go.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "build_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Log line content
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    // Log level — INFO, ERROR, WARN
    @Column(nullable = false)
    private String level;

    // Kis deployment ka log hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
