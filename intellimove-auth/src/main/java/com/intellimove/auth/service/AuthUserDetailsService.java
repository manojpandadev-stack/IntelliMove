package com.intellimove.auth.service;

import com.intellimove.auth.entity.AuthUser;
import com.intellimove.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Loads user details from the auth_users table for Spring Security login.
 * Marked @Primary so it takes precedence over the generic fallback.
 */
@Service
@Primary
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthUserRepository authUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AuthUser authUser = authUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        return User.builder()
                .username(authUser.getEmail())
                .password(authUser.getPassword())
                .authorities(authUser.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList()))
                .accountExpired(false)
                .accountLocked(authUser.isAccountLocked())
                .credentialsExpired(false)
                .disabled(!authUser.isEnabled())
                .build();
    }
}
