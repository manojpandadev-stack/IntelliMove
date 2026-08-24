package com.intellimove.location.config;

import com.intellimove.common.security.JwtTokenProvider;
import com.intellimove.location.handler.LocationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket configuration with JWT-based authentication at the handshake phase.
 * The JWT is validated before the WebSocket connection is established, preventing
 * unauthenticated connections. Authorized identity is stored in session attributes
 * for per-message authorization checks (subscription authorization in the handler).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LocationWebSocketHandler locationWebSocketHandler;
    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketConfig(LocationWebSocketHandler locationWebSocketHandler,
                           JwtTokenProvider jwtTokenProvider) {
        this.locationWebSocketHandler = locationWebSocketHandler;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(locationWebSocketHandler, "/ws/location")
                .addInterceptors(jwtHandshakeInterceptor())
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000");
    }

    /**
     * Handshake interceptor that validates the JWT before upgrading to WebSocket.
     * The token can be supplied as:
     *   - Authorization: Bearer &lt;token&gt; header
     *   - token query parameter (for browser WebSocket clients)
     */
    private HandshakeInterceptor jwtHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request,
                                           ServerHttpResponse response,
                                           WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) {
                String token = extractToken(request);
                if (token == null || !jwtTokenProvider.validateToken(token)) {
                    return false;
                }
                String tokenType = jwtTokenProvider.getTokenType(token);
                if (!"access".equals(tokenType)) {
                    return false;
                }
                String userId = jwtTokenProvider.getUserIdFromToken(token);
                String roles = jwtTokenProvider.getRolesFromToken(token);
                attributes.put("userId", userId);
                attributes.put("roles", roles != null ? roles : "");
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Exception exception) {
                // no-op
            }

            private String extractToken(ServerHttpRequest request) {
                // Check Authorization header
                String authHeader = request.getHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
                // Check token query parameter from the URI
                String query = request.getURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] parts = param.split("=");
                        if (parts.length == 2 && "token".equals(parts[0])) {
                            return parts[1];
                        }
                    }
                }
                return null;
            }
        };
    }
}


