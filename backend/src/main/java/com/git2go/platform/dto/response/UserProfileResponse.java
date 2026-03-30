package com.git2go.platform.dto.response;

import com.git2go.platform.enums.AuthProvider;
import com.git2go.platform.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class UserProfileResponse {
    private UUID id;
    private String name;
    private String email;
    private String avatarUrl;
    private Role role;
    private AuthProvider authProvider;
    // Password NAHI hai yahan — ye intentional hai, security ke liye
}
