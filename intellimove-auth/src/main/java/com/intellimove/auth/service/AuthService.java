package com.intellimove.auth.service;

import com.intellimove.auth.entity.AuthUser;
import com.intellimove.auth.repository.AuthUserRepository;
import com.intellimove.common.dto.auth.AuthResponse;
import com.intellimove.common.dto.auth.LoginRequest;
import com.intellimove.common.dto.auth.RefreshTokenRequest;
import com.intellimove.common.dto.auth.RegisterRequest;
import com.intellimove.common.enums.Role;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.common.security.JwtTokenProvider;
import com.intellimove.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "bl:token:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 30;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (authUserRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "Email is already registered");
        }

        Set<Role> roles = resolveRoles(request.getRole());

        AuthUser authUser = AuthUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(roles)
                .enabled(true)
                .build();

        authUser = authUserRepository.save(authUser);
        log.info("User registered: {}", authUser.getEmail());

        UserPrincipal principal = buildUserPrincipal(authUser);
        String userId = authUser.getId().toString();
        String accessToken = jwtTokenProvider.generateAccessToken(principal, userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal, userId);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs(accessToken) - System.currentTimeMillis())
                .user(buildUserInfo(authUser))
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AuthUser authUser = authUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid email or password"));

        if (authUser.isAccountLocked()) {
            throw new BusinessException("ACCOUNT_LOCKED",
                    "Account is locked due to too many failed login attempts. Try again later.");
        }

        if (!authUser.isEnabled()) {
            throw new BusinessException("ACCOUNT_DISABLED", "Account is disabled");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            handleFailedLogin(authUser);
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        authUser.setFailedLoginAttempts(0);
        authUserRepository.save(authUser);

        UserPrincipal principal = buildUserPrincipal(authUser);
        String userId = authUser.getId().toString();
        String accessToken = jwtTokenProvider.generateAccessToken(principal, userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal, userId);

        log.info("User logged in: {}", authUser.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs(accessToken) - System.currentTimeMillis())
                .user(buildUserInfo(authUser))
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_TOKEN", "Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException("INVALID_TOKEN", "Token is not a refresh token");
        }

        if (isTokenBlacklisted(refreshToken)) {
            throw new BusinessException("TOKEN_REVOKED", "Refresh token has been revoked");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        AuthUser authUser = authUserRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", username));

        UserPrincipal principal = buildUserPrincipal(authUser);
        String userId = authUser.getId().toString();
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal, userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal, userId);

        blacklistToken(refreshToken);

        log.info("Token refreshed for user: {}", username);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs(newAccessToken) - System.currentTimeMillis())
                .user(buildUserInfo(authUser))
                .build();
    }

    @Transactional
    public void logout(String accessToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7);
        }
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            blacklistToken(accessToken);
            log.info("User logged out, token blacklisted");
        }
    }

    private void handleFailedLogin(AuthUser authUser) {
        // Use independent transaction so rollback of the outer transaction doesn't lose the count
        var independentTx = new TransactionTemplate(
                transactionTemplate.getTransactionManager());
        independentTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independentTx.executeWithoutResult(status -> {
            int attempts = authUser.getFailedLoginAttempts() + 1;
            authUser.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                authUser.setAccountLocked(true);
                log.warn("Account locked for user: {} after {} failed attempts",
                        authUser.getEmail(), attempts);
            }
            authUserRepository.save(authUser);
        });
    }

    private boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }

    private void blacklistToken(String token) {
        long expirationMs = jwtTokenProvider.getExpirationMs(token);
        long ttlMs = expirationMs - System.currentTimeMillis();
        if (ttlMs > 0) {
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + token, "1",
                    Duration.ofMillis(ttlMs));
        }
    }

    private Set<Role> resolveRoles(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return Set.of(Role.CUSTOMER);
        }
        try {
            Role role = Role.valueOf(roleStr.toUpperCase());
            if (role == Role.SUPER_ADMIN) {
                // SUPER_ADMIN must be created via seed data, not public registration
                return Set.of(Role.CUSTOMER);
            }
            return Set.of(role);
        } catch (IllegalArgumentException e) {
            return Set.of(Role.CUSTOMER);
        }
    }

    private UserPrincipal buildUserPrincipal(AuthUser user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .enabled(user.isEnabled())
                .build();
    }

    private AuthResponse.UserInfo buildUserInfo(AuthUser user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRoles().iterator().next().name())
                .build();
    }
}
