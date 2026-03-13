package com.deployment.Git2Go.service;

import com.deployment.Git2Go.dto.request.LoginRequest;
import com.deployment.Git2Go.dto.request.RegisterRequest;
import com.deployment.Git2Go.dto.response.AuthResponse;
import com.deployment.Git2Go.dto.response.UserResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserResponse getCurrentUser(String email);
}