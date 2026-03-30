package com.git2go.platform.service;

import com.git2go.platform.audit.Auditable;
import com.git2go.platform.dto.request.LoginRequest;
import com.git2go.platform.dto.request.RefreshTokenRequest;
import com.git2go.platform.dto.request.SignupRequest;
import com.git2go.platform.dto.response.AuthResponse;
import com.git2go.platform.dto.response.UserProfileResponse;
import com.git2go.platform.entity.RefreshToken;
import com.git2go.platform.entity.User;
import com.git2go.platform.enums.AuthProvider;
import com.git2go.platform.enums.Role;
import com.git2go.platform.exception.ApiException;
import com.git2go.platform.repository.UserRepository;
import com.git2go.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;

    @Auditable(action = "USER_SIGNUP")
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false) // Verified nahi hai abhi
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {}", savedUser.getEmail());

        // Verification email bhejo — welcome email verification ke baad jaayegi
        emailVerificationService.sendVerificationEmail(savedUser);

        // Signup pe JWT dete hain but limited access — sirf verify email endpoint accessible hoga
        return buildAuthResponse(savedUser);
    }

    @Auditable(action = "USER_LOGIN")
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        // Email verified nahi hai toh login allow nahi (LOCAL users ke liye)
        if (user.getAuthProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            throw new ApiException("Please verify your email before logging in. Check your inbox.", HttpStatus.FORBIDDEN);
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.verifyRefreshToken(request.getRefreshToken());
        User user = refreshToken.getUser();

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Auditable(action = "USER_LOGOUT")
    public void logout(String userEmail) {
        refreshTokenService.deleteByUser(userEmail);
        log.info("User logged out: {}", userEmail);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .user(profile)
                .build();
    }
}
