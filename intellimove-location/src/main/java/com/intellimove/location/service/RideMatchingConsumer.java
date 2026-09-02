package com.intellimove.location.service;

import com.intellimove.common.event.DomainEvent;
import com.intellimove.common.event.DriverAssignedEvent;
import com.intellimove.common.event.DriverRejectedEvent;
import com.intellimove.common.event.RideCancelledEvent;
import com.intellimove.common.event.RideCompletedEvent;
import com.intellimove.common.event.RideRequestedEvent;
import com.intellimove.location.handler.LocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes RIDE_REQUESTED events from the ride service (via the outbox →
 * Kafka pipeline) and automatically searches for the best nearby driver.
 *
 * This closes the dispatch loop: ride creation alone no longer leaves a ride
 * stuck in REQUESTED — the location service proactively runs Redis GEO matching
 * and assigns a driver through the Ride Service's internal endpoint.
 *
 * Idempotency: duplicate event deliveries are safe because the internal
 * assign endpoint enforces the ride state machine (REQUESTED -> DRIVER_ASSIGNED);
 * an already-assigned ride rejects the transition and the consumer stops retrying.
 *
 * If no driver is available yet (common right after a rider books), the search
 * is retried with a short backoff so drivers who come online moments later are
 * still matched. After the bounded retries the ride stays REQUESTED and can
 * still be matched manually or cancelled by the customer.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RideMatchingConsumer {

    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 4000L;

    private final MatchingService matchingService;
    private final RideValidationService rideValidationService;
    private final DriverLocationService driverLocationService;
    private final LocationWebSocketHandler webSocketHandler;

    @KafkaListener(topics = "ride-events", groupId = "location-service")
    public void handleRideEvent(DomainEvent event) {
        // Free the driver for new matches when their ride ends.
        if (event instanceof RideCompletedEvent completed) {
            releaseDriver(completed.getDriverId(), "ride completed", completed.getRideId());
            return;
        }
        if (event instanceof RideCancelledEvent cancelled) {
            releaseDriver(cancelled.getDriverId(), "ride cancelled", cancelled.getRideId());
            return;
        }
        // An assigned driver rejected the request: exclude them from THIS ride,
        // free them for other rides, and immediately re-run the existing search
        // so another eligible driver is selected (Uber-style reassignment).
        if (event instanceof DriverRejectedEvent rejected) {
            handleDriverRejected(rejected);
            return;
        }
        // Real-time push to the assigned driver over the existing WebSocket:
        // the driver dashboard listens on its own channel and refreshes its
        // ride data via the existing REST API (no fabricated payloads).
        if (event instanceof DriverAssignedEvent assigned) {
            notifyDriverAssigned(assigned);
            return;
        }
        if (!(event instanceof RideRequestedEvent requested)) {
            return; // other ride lifecycle events are handled elsewhere / ignored
        }
        if (requested.getRideId() == null) {
            log.warn("RideRequestedEvent without rideId, skipping (correlationId={})",
                    event.getCorrelationId());
            return;
        }

        String rideId = requested.getRideId();

        // Skip stale events (replayed backlog / already completed or cancelled
        // rides) instantly instead of burning match attempts on them.
        if (!rideValidationService.isRideAssignable(rideId)) {
            log.info("Ride {} is no longer assignable — skipping driver search", rideId);
            return;
        }

        log.info("Driver search started for ride {} (correlationId={})", rideId, event.getCorrelationId());
        runDriverSearch(rideId, requested.getPickupLatitude(), requested.getPickupLongitude(),
                requested.getRideType());
    }

    /**
     * The existing bounded driver-search loop, reused by both the initial
     * RIDE_REQUESTED dispatch and post-rejection reassignment.
     */
    private void runDriverSearch(String rideId, double pickupLat, double pickupLng, String rideType) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var result = matchingService.findAndLockDriver(rideId, pickupLat, pickupLng, rideType);

            if (result.isEmpty()) {
                log.info("No available drivers near ride {} (attempt {}/{})", rideId, attempt, MAX_ATTEMPTS);
                sleepBeforeNextAttempt(attempt);
                continue;
            }

            String driverId = result.get().driverId();
            boolean assigned = rideValidationService.assignDriverToRide(rideId, driverId);
            if (assigned) {
                // Commit the driver so concurrent searches skip them immediately
                // (the short distributed lock alone would expire mid-assignment).
                driverLocationService.associateDriverWithRide(driverId, rideId);
                log.info("Driver {} auto-assigned to ride {} (score={}, distanceKm={})",
                        driverId, rideId, result.get().score(), result.get().distanceKm());
                return;
            }

            // Either a concurrent assignment won (idempotent no-op) or the ride
            // can no longer be assigned - do not keep retrying in either case.
            matchingService.releaseDriverLock(driverId);
            log.info("Assignment rejected for ride {} (already assigned or invalid state) — stopping search", rideId);
            return;
        }
        log.warn("Driver search exhausted {} attempts for ride {} — ride remains awaiting a driver",
                MAX_ATTEMPTS, rideId);
    }

    private void handleDriverRejected(DriverRejectedEvent rejected) {
        String rideId = rejected.getRideId();
        String driverId = rejected.getDriverId();
        if (rideId == null || driverId == null) {
            log.warn("DriverRejectedEvent without rideId/driverId, skipping");
            return;
        }
        // The ride was returned to REQUESTED by the Ride Service before this
        // event was published; skip if it became stale in the meantime.
        if (!rideValidationService.isRideAssignable(rideId)) {
            log.info("Rejected ride {} is no longer assignable — skipping reassignment", rideId);
            releaseDriver(driverId, "ride rejected (ride no longer assignable)", rideId);
            return;
        }
        // Exclude the rejecting driver for THIS ride only (they stay fully
        // available for all other rides), then free them and re-run the
        // existing search loop so the next best eligible driver is offered.
        matchingService.excludeDriverForRide(rideId, driverId);
        releaseDriver(driverId, "ride request rejected", rideId);
        log.info("Driver {} rejected ride {} — reassigning to another eligible driver", driverId, rideId);
        runDriverSearch(rideId, rejected.getPickupLatitude(), rejected.getPickupLongitude(),
                rejected.getRideType());
    }

    private void notifyDriverAssigned(DriverAssignedEvent assigned) {
        if (assigned.getDriverId() == null || assigned.getRideId() == null) {
            return;
        }
        try {
            webSocketHandler.broadcastToUser(assigned.getDriverId(), Map.of(
                    "type", "ride_assigned",
                    "rideId", assigned.getRideId(),
                    "driverId", assigned.getDriverId(),
                    "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            // Delivery is best-effort: the driver dashboard also polls the
            // driver-rides REST endpoint as the existing fallback.
            log.warn("Failed to push ride_assigned to driver {}: {}", assigned.getDriverId(), e.getMessage());
        }
    }

    private void sleepBeforeNextAttempt(int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void releaseDriver(String driverId, String cause, String rideId) {
        if (driverId == null || driverId.isBlank()) {
            return;
        }
        try {
            driverLocationService.clearDriverRide(driverId);
            matchingService.releaseDriverLock(driverId);
            log.info("Driver {} released ({}) for ride {}", driverId, cause, rideId);
        } catch (Exception e) {
            log.error("Failed to release driver {} after {}: {}", driverId, cause, e.getMessage());
        }
    }
}
