package com.git2go.platform.dto.response;

import com.git2go.platform.enums.AuthProvider;
import com.git2go.platform.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class AdminUserResponse {
    private UUID id;
    private String name;
    private String email;
    private String avatarUrl;
    private Role role;
    private AuthProvider authProvider;
    private boolean emailVerified;
    private int projectCount;
    private LocalDateTime createdAt;
}
