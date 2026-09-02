package com.intellimove.location.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits the WebSocket upgrade request for /ws/location through the servlet
 * security filter chain.
 *
 * WHY: browser WebSocket clients cannot set an Authorization header on the
 * upgrade request, so the JwtAuthenticationFilter would 403 every handshake
 * before it reaches the WebSocket layer. Authentication is still strictly
 * enforced — by {@link WebSocketConfig}'s JwtHandshakeInterceptor, which
 * validates the JWT (access token only) from the ?token= query parameter
 * before the upgrade completes — and per-message subscription authorization
 * stays in LocationWebSocketHandler. A missing/expired/forged token therefore
 * still results in a rejected handshake.
 */
@Configuration
public class LocationServiceWebSocketSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain webSocketSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityMatchers(matchers -> matchers.requestMatchers("/ws/location"))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
