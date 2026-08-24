package com.intellimove.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT authentication filter that extracts user identity and roles directly from JWT claims.
 * Does NOT call UserDetailsService — all auth info comes from the validated token.
 *
 * The principal is set to the userId claim (UUID) extracted from the JWT, NOT from the
 * X-User-Id header. This ensures identity is always derived from the signed token,
 * preventing header-forging attacks when services are accessed directly (bypassing
 * the gateway). Controllers should use SecurityUtils.getCurrentUserId() instead of
 * reading X-User-Id headers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String tokenType = jwtTokenProvider.getTokenType(token);
                if ("access".equals(tokenType)) {
                    String userId = jwtTokenProvider.getUserIdFromToken(token);
                    String email = jwtTokenProvider.getEmailFromToken(token);
                    String rolesStr = jwtTokenProvider.getRolesFromToken(token);

                    List<SimpleGrantedAuthority> authorities = List.of();
                    if (StringUtils.hasText(rolesStr)) {
                        authorities = Arrays.stream(rolesStr.split(","))
                                .filter(StringUtils::hasText)
                                .map(role -> new SimpleGrantedAuthority(
                                        role.startsWith("ROLE_") ? role : "ROLE_" + role.trim()))
                                .collect(Collectors.toList());
                    }

                    // Principal is the userId from the JWT, falling back to email
                    // if userId claim is absent (backward compatibility).
                    String principal = StringUtils.hasText(userId) ? userId : email;
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, authorities);
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.debug("Could not set user authentication in security context: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
