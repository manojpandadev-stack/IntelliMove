package com.intellimove.gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Public endpoints that don't require JWT
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/health",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip JWT validation for public endpoints
        for (String publicPath : PUBLIC_PATHS) {
            if (pathMatcher.match(publicPath, path)) {
                return chain.filter(exchange);
            }
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Jws<Claims> claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            Claims payload = claims.getPayload();
            String username = payload.getSubject(); // email
            String roles = payload.get("roles", String.class);
            String userId = payload.get("userId", String.class);

            if (!StringUtils.hasText(username)) {
                return unauthorizedResponse(exchange, "Invalid token: no subject");
            }

            // Forward user info to downstream services
            ServerHttpRequest.Builder reqBuilder = request.mutate()
                    .header("X-User-Email", username)
                    .header("X-User-Roles", StringUtils.hasText(roles) ? roles : "");

            // Forward UUID userId if present in JWT
            if (StringUtils.hasText(userId)) {
                reqBuilder.header("X-User-Id", userId);
            } else {
                // Fallback: forward email for backward compatibility
                reqBuilder.header("X-User-Id", username);
            }

            return chain.filter(exchange.mutate().request(reqBuilder.build()).build());

        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            return unauthorizedResponse(exchange, "Token expired");
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return unauthorizedResponse(exchange, "Invalid token");
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"success\":false,\"message\":\"%s\",\"status\":401}", message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
