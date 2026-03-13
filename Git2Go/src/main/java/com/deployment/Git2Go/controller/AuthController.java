package com.deployment.Git2Go.controller;

import com.deployment.Git2Go.dto.request.LoginRequest;
import com.deployment.Git2Go.dto.request.RegisterRequest;
import com.deployment.Git2Go.dto.response.ApiResponse;
import com.deployment.Git2Go.dto.response.AuthResponse;
import com.deployment.Git2Go.dto.response.UserResponse;
import com.deployment.Git2Go.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Register with email + password
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Registered successfully", response));
    }

    // Login with email + password
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // Get current logged-in user
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            Authentication authentication) {
        UserResponse response =
            authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // GitHub OAuth login — Spring handles /oauth2/authorization/github
    // After success, OAuth2SuccessHandler redirects to frontend with JWT
    // This endpoint just tells the frontend where to go
    @GetMapping("/github")
    public ResponseEntity<ApiResponse<String>> githubLogin() {
        return ResponseEntity.ok(
            ApiResponse.success("Redirect to GitHub OAuth",
                "/oauth2/authorization/github")
        );
    }
}