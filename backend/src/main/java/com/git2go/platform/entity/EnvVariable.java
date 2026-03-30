package com.git2go.platform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "env_variables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String key;

    // TODO: Production me ye encrypted hona chahiye (jasypt library ya DB-level encryption)
    @Column(nullable = false)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
