package com.intellimove.location.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.location.dto.RideEtaResponse;
import com.intellimove.location.service.RideEtaService;
import com.intellimove.location.service.RideValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Live pickup ETA endpoint. The Location Service owns the real-time driver
 * coordinates (Redis GEO) and computes the ETA against the ride pickup.
 *
 * <p>Authorization is enforced against the <em>authenticated</em> user (JWT):
 * only the ride's customer, its assigned driver, or an admin may read a given
 * ride's ETA — never another customer (IDOR). Error responses follow the
 * existing {@link ApiResponse} conventions with meaningful HTTP statuses
 * rather than 500s.</p>
 */
@RestController
@RequestMapping("/api/v1/location")
@RequiredArgsConstructor
@Slf4j
public class RideEtaController {

    private final RideEtaService rideEtaService;
    private final RideValidationService rideValidationService;

    @GetMapping("/ride/{rideId}/eta")
    public ResponseEntity<ApiResponse<RideEtaResponse>> getRideEta(@PathVariable String rideId) {
        UUID rideUuid = parseRideId(rideId);
        if (rideUuid == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid ride ID"));
        }

        String userId = SecurityUtils.getCurrentUserIdString();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }

        // IDOR: only the ride's customer/driver or an admin may read its ETA.
        boolean admin = SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN");
        if (!admin && !rideValidationService.isUserAuthorizedForRide(rideUuid.toString(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to view this ride's ETA"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(rideEtaService.computeEta(rideUuid)));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (RedisConnectionFailureException | RestClientException ex) {
            log.warn("ETA computation failed for ride {}: {}", rideUuid, ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Location or ride service unavailable"));
        }
    }

    private UUID parseRideId(String rideId) {
        try {
            return UUID.fromString(rideId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}