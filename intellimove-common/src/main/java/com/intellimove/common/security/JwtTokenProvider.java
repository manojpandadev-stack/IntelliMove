package com.intellimove.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long jwtExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UserDetails userDetails, String userId) {
        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return generateToken(userDetails.getUsername(), roles, jwtExpirationMs, "access", userId);
    }

    public String generateRefreshToken(UserDetails userDetails, String userId) {
        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return generateToken(userDetails.getUsername(), roles, refreshExpirationMs, "refresh", userId);
    }

    /** Backward-compatible overloads without userId */
    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(userDetails, null);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(userDetails, null);
    }

    private String generateToken(String subject, String roles, long expirationMs,
                                  String tokenType, String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim("roles", roles)
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key);
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getPayload().getSubject();
    }

    public String getTokenType(String token) {
        return getClaims(token).getPayload().get("tokenType", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = getClaims(token);
            return !claims.getPayload().getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public long getExpirationMs(String token) {
        return getClaims(token).getPayload().getExpiration().getTime();
    }

    public String getRolesFromToken(String token) {
        return getClaims(token).getPayload().get("roles", String.class);
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getPayload().getSubject();
    }

    public String getUserIdFromToken(String token) {
        return getClaims(token).getPayload().get("userId", String.class);
    }

    private Jws<Claims> getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}
