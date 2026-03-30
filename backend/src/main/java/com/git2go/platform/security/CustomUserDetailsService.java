package com.git2go.platform.security;

import com.git2go.platform.entity.User;
import com.git2go.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security ko batata hai ki user kaise load karna hai DB se.
 *
 * Jab JwtAuthenticationFilter me loadUserByUsername() call hota hai,
 * ye class actually DB se user fetch karti hai aur Spring Security
 * ke format (UserDetails) me convert karke return karti hai.
 *
 * Interview: "UserDetailsService implement kiya hai — ye Spring Security ka
 * contract hai. loadUserByUsername() me DB se user fetch karta hoon aur
 * UserDetails object return karta hoon with user's roles as GrantedAuthority."
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword() != null ? user.getPassword() : "", // OAuth users ke paas password nahi hota
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
