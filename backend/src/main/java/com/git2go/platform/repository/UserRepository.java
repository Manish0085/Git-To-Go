package com.git2go.platform.repository;

import com.git2go.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.git2go.platform.enums.AuthProvider;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Spring Data JPA automatically generates: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // Check if email already exists — signup me duplicate check ke liye
    boolean existsByEmail(String email);

    // OAuth2 login me use hoga — provider + providerId se user find karna
    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    // Email verification — token se user find karo
    Optional<User> findByVerificationToken(String verificationToken);
}
