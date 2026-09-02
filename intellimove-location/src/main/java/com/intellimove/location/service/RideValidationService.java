package com.intellimove.location.service;

import com.intellimove.location.dto.RideEtaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Validates that a user is authorized to view/subscribe to a specific ride.
 * Calls the Ride Service to check whether the user is the customer or assigned
 * driver for the given ride. This prevents IDOR-style attacks over WebSocket
 * subscriptions where a client could subscribe to any ride channel.
 */
@Service
@Slf4j
public class RideValidationService {

    private final String rideServiceUrl;
    private final RestTemplate restTemplate;

    public RideValidationService(@Value("${RIDE_SERVICE_URL:http://localhost:${RIDE_SERVICE_PORT:8084}}")
                                 String rideServiceUrl,
                                 RestTemplate restTemplate) {
        this.rideServiceUrl = rideServiceUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Assign a driver to a ride by calling the Ride Service's internal assign endpoint.
     */
    public boolean assignDriverToRide(String rideId, String driverId) {
        if (rideId == null || driverId == null) {
            return false;
        }
        try {
            String url = rideServiceUrl + "/api/v1/rides/internal/" + rideId + "/assign/" + driverId;
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object success = response.getBody().get("success");
                return Boolean.TRUE.equals(success);
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to assign driver {} to ride {}: {}", driverId, rideId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if the given user ID is authorized (is customer or assigned driver)
     * for the given ride ID.
     */
    public boolean isUserAuthorizedForRide(String rideId, String userId) {
        if (rideId == null || userId == null) {
            return false;
        }
        try {
            String url = rideServiceUrl + "/api/v1/rides/internal/" + rideId + "/authorized/" + userId;
            ResponseEntity<Boolean> response = restTemplate.getForEntity(
                    url, Boolean.class);
            Boolean authorized = response.getBody();
            return Boolean.TRUE.equals(authorized);
        } catch (Exception e) {
            log.warn("Failed to validate ride authorization for ride {} user {}: {}",
                    rideId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Check whether the ride can still accept a driver assignment
     * (state REQUESTED/MATCHING). Lets the matching consumer skip stale
     * events for already completed/cancelled rides without any match work.
     */
    public boolean isRideAssignable(String rideId) {
        if (rideId == null) {
            return false;
        }
        try {
            String url = rideServiceUrl + "/api/v1/rides/internal/" + rideId + "/assignable";
            ResponseEntity<Boolean> response = restTemplate.getForEntity(url, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            // If the check fails, be conservative and let the normal flow proceed:
            // the assign endpoint still enforces the state machine as a backstop.
            log.warn("Assignable check failed for ride {}: {}", rideId, e.getMessage());
            return true;
        }
    }

    /**
     * Fetch the minimal ride context (driver, pickup coords, state) used to
     * compute a live pickup ETA.
     *
     * @return {@link Optional#empty()} when the ride does not exist (HTTP 404);
     *         throws when the Ride Service is unavailable (HTTP 5xx / network),
     *         so callers can surface a 503 rather than a misleading 4xx.
     */
    public Optional<RideEtaContext> getRideEtaContext(String rideId) {
        if (rideId == null) {
            return Optional.empty();
        }
        String url = rideServiceUrl + "/api/v1/rides/internal/" + rideId + "/eta-context";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof Map<?, ?> map) {
                    return Optional.of(new RideEtaContext(
                            map.get("driverId") != null ? String.valueOf(map.get("driverId")) : null,
                            map.get("pickupLatitude") instanceof Number lat ? lat.doubleValue() : 0d,
                            map.get("pickupLongitude") instanceof Number lng ? lng.doubleValue() : 0d,
                            map.get("status") != null ? String.valueOf(map.get("status")) : null));
                }
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            // Ride does not exist → let the caller return 404.
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            // Any other ride-service 4xx: treat as not found / bad input.
            log.warn("Ride service returned {} for ETA context of ride {}: {}",
                    e.getStatusCode(), rideId, e.getMessage());
            return Optional.empty();
        }
        // RestClientException (transport-level) intentionally propagates so the
        // caller can map it to HTTP 503 "ride service unavailable".
    }
}
