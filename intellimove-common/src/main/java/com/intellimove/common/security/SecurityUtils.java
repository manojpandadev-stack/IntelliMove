package com.intellimove.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Extracts authenticated user identity from the Spring Security context.
 * Downstream services MUST derive identity from the validated JWT principal
 * (set by JwtAuthenticationFilter), never from client-supplied headers like
 * X-User-Id. This prevents header-forging when a service port is accessed
 * directly, bypassing the API Gateway.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the authenticated user's UUID, derived from the JWT principal.
     * Returns null if no authentication is present.
     */
    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String principal = auth.getPrincipal().toString();
        try {
            return UUID.fromString(principal);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the authenticated user's UUID as a string, derived from the JWT principal.
     */
    public static String getCurrentUserIdString() {
        UUID id = getCurrentUserId();
        return id != null ? id.toString() : null;
    }

    /**
     * Returns the authenticated user's email (JWT subject).
     */
    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }

    /**
     * Returns the roles of the authenticated user.
     */
    public static List<String> getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return List.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toList());
    }

    /**
     * Convenience: checks if the current user has the given role.
     */
    public static boolean hasRole(String role) {
        return getCurrentRoles().contains(role);
    }

    /**
     * Convenience: checks if the current user has any of the given roles.
     */
    public static boolean hasAnyRole(String... roles) {
        List<String> userRoles = getCurrentRoles();
        for (String role : roles) {
            if (userRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
