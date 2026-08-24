package com.intellimove.location.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

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
}
