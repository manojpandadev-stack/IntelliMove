package com.intellimove.ride.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.dto.PagedResponse;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.ride.dto.CancelRideRequest;
import com.intellimove.ride.dto.CreateRideRequest;
import com.intellimove.ride.dto.FareEstimateResponse;
import com.intellimove.ride.dto.RideEtaContext;
import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

        @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RideResponse>> requestRide(
            @Valid @RequestBody CreateRideRequest request) {
        // Identity derived from the authenticated JWT principal (SecurityUtils),
        // NOT from the X-User-Id header. This prevents forged identity.
        UUID customerId = SecurityUtils.getCurrentUserId();
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        RideResponse ride = rideService.requestRide(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ride requested", ride));
    }

    @GetMapping("/estimate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FareEstimateResponse>> estimateFare(
            @RequestParam("pickupLat") double pickupLat,
            @RequestParam("pickupLng") double pickupLng,
            @RequestParam("dropoffLat") double dropoffLat,
            @RequestParam("dropoffLng") double dropoffLng) {
        FareEstimateResponse estimate = rideService.estimateFare(
                pickupLat, pickupLng, dropoffLat, dropoffLng);
        return ResponseEntity.ok(ApiResponse.success(estimate));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RideResponse>> getRide(@PathVariable UUID id) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        // IDOR fix: verify the caller is authorized to view this ride
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")
                && !rideService.isUserAuthorizedForRide(id, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to view this ride"));
        }
        RideResponse ride = rideService.getRide(id);
        return ResponseEntity.ok(ApiResponse.success(ride));
    }

        @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RideResponse>> cancelRide(
            @PathVariable UUID id,
            @Valid @RequestBody CancelRideRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        RideResponse ride = rideService.cancelRide(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Ride cancelled", ride));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RideResponse>> assignDriver(
            @PathVariable UUID id,
            @RequestParam UUID driverId) {
        RideResponse ride = rideService.assignDriver(id, driverId);
        return ResponseEntity.ok(ApiResponse.success("Driver assigned", ride));
    }

        @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<RideResponse>> acceptRide(
            @PathVariable UUID id) {
        UUID driverId = SecurityUtils.getCurrentUserId();
        if (driverId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        RideResponse ride = rideService.driverAccept(id, driverId);
        return ResponseEntity.ok(ApiResponse.success("Ride accepted", ride));
    }

    /**
     * Assigned driver rejects an incoming ride request. The ride returns to
     * REQUESTED (it is NOT cancelled) so matching can select another driver.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<RideResponse>> rejectRide(
            @PathVariable UUID id) {
        UUID driverId = SecurityUtils.getCurrentUserId();
        if (driverId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        RideResponse ride = rideService.driverReject(id, driverId);
        return ResponseEntity.ok(ApiResponse.success("Ride request rejected", ride));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<RideResponse>> startTrip(
            @PathVariable UUID id) {
        UUID driverId = SecurityUtils.getCurrentUserId();
        if (driverId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        RideResponse ride = rideService.startTrip(id, driverId);
        return ResponseEntity.ok(ApiResponse.success("Trip started", ride));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<RideResponse>> completeTrip(
            @PathVariable UUID id) {
        UUID driverId = SecurityUtils.getCurrentUserId();
        if (driverId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        RideResponse ride = rideService.completeTrip(id, driverId);
        return ResponseEntity.ok(ApiResponse.success("Trip completed", ride));
    }

        @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<RideResponse>>> getCustomerRides(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        // IDOR fix: customers can only view their own rides
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")
                && !currentUserId.equals(customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to view these rides"));
        }
        PagedResponse<RideResponse> rides = rideService.getCustomerRides(customerId, page, size);
        return ResponseEntity.ok(ApiResponse.success(rides));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<RideResponse>>> getDriverRides(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        // IDOR fix: drivers can only view their own rides
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")
                && !currentUserId.equals(driverId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to view these rides"));
        }
        PagedResponse<RideResponse> rides = rideService.getDriverRides(driverId, page, size);
        return ResponseEntity.ok(ApiResponse.success(rides));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<RideResponse>>> getAllRides(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<RideResponse> rides = rideService.getAllRides(page, size);
        return ResponseEntity.ok(ApiResponse.success(rides));
    }

    /**
     * Internal endpoint for the Location Service's automatic driver matching.
     * Assigns a matched driver to a ride. No RBAC required because this is
     * an internal service-to-service call only reachable from the internal network.
     */
    @PostMapping("/internal/{rideId}/assign/{driverId}")
    public ResponseEntity<ApiResponse<RideResponse>> internalAssignDriver(
            @PathVariable UUID rideId,
            @PathVariable UUID driverId) {
        RideResponse ride = rideService.assignDriver(rideId, driverId);
        return ResponseEntity.ok(ApiResponse.success("Driver assigned", ride));
    }

    /**
     * Internal endpoint for the Location Service's WebSocket subscription
     * authorization. Verifies that the given user is the customer or assigned
     * driver of the given ride. Does NOT trust client-supplied identity.
     */
    @GetMapping("/internal/{rideId}/authorized/{userId}")
    public ResponseEntity<Boolean> checkRideAuthorization(
            @PathVariable UUID rideId,
            @PathVariable UUID userId) {
        boolean authorized = rideService.isUserAuthorizedForRide(rideId, userId);
        return ResponseEntity.ok(authorized);
    }

    /**
     * Internal endpoint for the Location Service's live ETA feature. Returns
     * the minimal ride context (assigned driver, pickup coordinates, state)
     * needed to compute a pickup ETA from the driver's real-time GEO position.
     * No RBAC because it is a service-to-service call (same convention as the
     * other {@code /internal/**} endpoints).
     */
    @GetMapping("/internal/{rideId}/eta-context")
    public ResponseEntity<ApiResponse<RideEtaContext>> getRideEtaContext(
            @PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success(rideService.getEtaContext(rideId)));
    }

    /**
     * Internal endpoint for the Location Service's automatic driver matching.
     * Reports whether the ride can still accept a driver assignment, i.e. its
     * state-machine transition to DRIVER_ASSIGNED is still possible
     * (REQUESTED or MATCHING). Lets the matching consumer skip stale Kafka
     * events for completed/cancelled rides instantly instead of burning
     * match attempts on them.
     */
    @GetMapping("/internal/{rideId}/assignable")
    public ResponseEntity<Boolean> isRideAssignable(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.isAssignable(rideId));
    }
}
