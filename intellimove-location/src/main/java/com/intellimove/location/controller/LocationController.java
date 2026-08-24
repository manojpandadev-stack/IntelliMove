package com.intellimove.location.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.location.dto.MatchDriversRequest;
import com.intellimove.location.dto.MatchResponse;
import com.intellimove.location.dto.UpdateLocationRequest;
import com.intellimove.location.security.LocationIdentity;
import com.intellimove.location.service.DriverLocationService;
import com.intellimove.location.service.MatchingService;
import com.intellimove.location.service.RideValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/location")
@RequiredArgsConstructor
@Validated
public class LocationController {

    private final DriverLocationService driverLocationService;
    private final MatchingService matchingService;
    private final RideValidationService rideValidationService;

    /**
     * Canonical location update. Identity is taken only from the JWT —
     * clients must not send a driver profile ID. Redis GEO members are
     * driver user IDs so matching can assign the same ID onto Ride.driverId.
     */
    @PostMapping("/update")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> updateMyLocation(
            @Valid @RequestBody UpdateLocationRequest request) {
        String driverUserId = LocationIdentity.currentDriverUserId();
        if (driverUserId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        applyLocationUpdate(driverUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated", null));
    }

    /**
     * Path-based update kept for compatibility. {@code driverId} MUST be the
     * authenticated driver <em>user</em> ID. Driver profile IDs are rejected (403).
     */
    @PostMapping("/driver/{driverId}/update")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> updateDriverLocation(
            @PathVariable String driverId,
            @Valid @RequestBody UpdateLocationRequest request) {
        String authenticatedUserId = LocationIdentity.currentDriverUserId();
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        if (!authenticatedUserId.equals(driverId)) {
            return ResponseEntity.status(403).body(ApiResponse.error(LocationIdentity.MISMATCH_MESSAGE));
        }

        applyLocationUpdate(authenticatedUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated", null));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<DriverLocationService.DriverLocation>> getDriverLocation(
            @PathVariable String driverId) {
        return driverLocationService.getDriverLocation(driverId)
                .map(loc -> ResponseEntity.ok(ApiResponse.success(loc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<DriverLocationService.DriverLocation>>> findNearbyDrivers(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        List<DriverLocationService.DriverLocation> drivers =
                driverLocationService.findNearbyDrivers(latitude, longitude, radiusKm);
        return ResponseEntity.ok(ApiResponse.success(drivers));
    }

    @GetMapping("/active-count")
    public ResponseEntity<ApiResponse<Long>> getActiveDriverCount() {
        return ResponseEntity.ok(ApiResponse.success(driverLocationService.getActiveDriverCount()));
    }

    @DeleteMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeDriverLocation(@PathVariable String driverId) {
        String currentUserId = LocationIdentity.currentDriverUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        if (!LocationIdentity.isAdmin() && !currentUserId.equals(driverId)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Cannot remove another driver's location"));
        }
        driverLocationService.removeDriverLocation(driverId);
        return ResponseEntity.ok(ApiResponse.success("Location removed", null));
    }

    /**
     * Automatic driver matching endpoint.
     * Finds the best available driver via Redis GEO, locks them with a distributed
     * lock, then assigns them to the ride via the Ride Service's internal endpoint.
     * The matched ID is the driver user ID stored in GEO.
     */
    @PostMapping("/match")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'SUPER_ADMIN', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<MatchResponse>> matchDriver(
            @Valid @RequestBody MatchDriversRequest request) {
        return matchingService.findAndLockDriver(
                request.getRideId(),
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getRideType())
                .map(result -> {
                    boolean assigned = rideValidationService.assignDriverToRide(
                            request.getRideId(), result.driverId());
                    if (assigned) {
                        return ResponseEntity.ok(ApiResponse.success(
                                new MatchResponse(result.driverId(), result.score(), result.distanceKm())));
                    } else {
                        matchingService.releaseDriverLock(result.driverId());
                        return ResponseEntity.<ApiResponse<MatchResponse>>ok(
                                ApiResponse.error("Driver found but assignment failed"));
                    }
                })
                .orElse(ResponseEntity.ok(ApiResponse.success("No drivers available", null)));
    }

    private void applyLocationUpdate(String driverUserId, UpdateLocationRequest request) {
        Map<String, String> metadata = request.getMetadata() != null ? request.getMetadata() : Map.of();
        driverLocationService.updateDriverLocation(
                driverUserId,
                request.getLatitude(),
                request.getLongitude(),
                metadata,
                request.getRideId());
    }
}
