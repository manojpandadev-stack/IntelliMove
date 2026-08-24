package com.intellimove.location.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellimove.location.service.DriverLocationService;
import com.intellimove.location.service.RideValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time location broadcasting.
 *
 * Security model:
 * - Each session is authenticated at handshake (JWT validated in WebSocketConfig).
 * - Session attributes carry "userId" and "roles" set during the handshake.
 * - Subscription to a ride channel authorizes only the ride customer or assigned driver.
 * - The authenticated identity is taken from the session, NEVER from client-supplied
 *   payload fields.
 */
@Component
@Slf4j
public class LocationWebSocketHandler extends TextWebSocketHandler {

    private final DriverLocationService driverLocationService;
    private final ObjectMapper objectMapper;
    private final RideValidationService rideValidationService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rideSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRide = new ConcurrentHashMap<>();

        public LocationWebSocketHandler(DriverLocationService driverLocationService,
                                    ObjectMapper objectMapper,
                                    RideValidationService rideValidationService) {
        this.driverLocationService = driverLocationService;
        this.objectMapper = objectMapper;
        this.rideValidationService = rideValidationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        rideSubscriptions.values().forEach(set -> set.remove(session.getId()));
        sessionToRide.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

        @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case "subscribe_ride" -> handleSubscribeRide(session, payload);
                case "unsubscribe_ride" -> handleUnsubscribeRide(session, payload);
                default -> {
                    // "driver_location" messages are no longer accepted via raw WS;
                    // drivers update location via the REST API which triggers broadcast.
                    log.warn("Unknown or unsupported message type: {}", type);
                }
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage(), e);
        }
    }

    private void handleSubscribeRide(WebSocketSession session, Map<String, Object> payload) {
        String rideId = (String) payload.get("rideId");
        String userId = (String) session.getAttributes().get("userId");
        String roles = (String) session.getAttributes().get("roles");

        if (rideId == null || userId == null) {
            sendError(session, "MISSING_RIDE_ID_OR_USER");
            return;
        }

        // Authorization: a user can only subscribe to their own ride.
        if (!isAuthorizedForRide(rideId, userId, roles)) {
            log.warn("Unauthorized subscription attempt: user {} -> ride {}", userId, rideId);
            sendError(session, "UNAUTHORIZED_SUBSCRIPTION");
            return;
        }

        // Only one active subscription per session (the current ride)
        String existingRide = sessionToRide.get(session.getId());
        if (existingRide != null) {
            unsubscribe(session.getId(), existingRide);
        }

        rideSubscriptions.computeIfAbsent(rideId, k -> ConcurrentHashMap.newKeySet())
                .add(session.getId());
        sessionToRide.put(session.getId(), rideId);
        log.debug("Session {} subscribed to ride {}", session.getId(), rideId);
    }

    private void handleUnsubscribeRide(WebSocketSession session, Map<String, Object> payload) {
        String rideId = (String) payload.get("rideId");
        if (rideId != null) {
            unsubscribe(session.getId(), rideId);
            sessionToRide.remove(session.getId());
        }
    }

    private void unsubscribe(String sessionId, String rideId) {
        Set<String> subs = rideSubscriptions.get(rideId);
        if (subs != null) {
            subs.remove(sessionId);
            if (subs.isEmpty()) {
                rideSubscriptions.remove(rideId);
            }
        }
    }

        /**
     * Check if the given user is authorized to subscribe to / receive updates for the ride.
     * A customer can view their own ride. A driver can view their assigned ride.
     * Admins can view any ride.
     */
    private boolean isAuthorizedForRide(String rideId, String userId, String roles) {
        // Admin override
        if (roles != null && roles.contains("ADMIN")) {
            return true;
        }

        // The rideValidationService checks whether userId is the customer or assigned driver
        // of the given ride, using the authoritative database (not client-supplied data).
        return rideValidationService.isUserAuthorizedForRide(rideId, userId);
    }

    /**
     * Broadcast a driver location update to all subscribers of the given ride.
     * This is called by DriverLocationService when a driver's location is updated
     * and the driver is associated with an active ride.
     */
    public void broadcastDriverLocation(String rideId, String driverId,
                                        double latitude, double longitude) {
        Set<String> subscriberIds = rideSubscriptions.getOrDefault(rideId, Set.of());
        Map<String, Object> msg = Map.of(
                "type", "driver_location_update",
                "rideId", rideId,
                "driverId", driverId,
                "latitude", latitude,
                "longitude", longitude,
                "timestamp", System.currentTimeMillis()
        );
        try {
            String json = objectMapper.writeValueAsString(msg);
            for (String sessionId : subscriberIds) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
            log.debug("Broadcasted location for ride {} to {} subscribers", rideId, subscriberIds.size());
        } catch (Exception e) {
            log.error("Error broadcasting location: {}", e.getMessage(), e);
        }
    }

    private void sendError(WebSocketSession session, String error) {
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("type", "error", "error", error));
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Could not send error to session: {}", e.getMessage());
        }
    }
}
