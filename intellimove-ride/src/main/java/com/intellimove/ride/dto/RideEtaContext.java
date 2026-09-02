package com.intellimove.ride.dto;

import com.intellimove.common.enums.RideStatus;

import java.util.UUID;

/**
 * Minimal ride context needed by the Location Service to compute a live
 * pickup ETA: the assigned driver, the pickup coordinates, and the current
 * ride state. Exposed via the internal service-to-service endpoint only —
 * the Location Service owns real-time driver coordinates and does the math.
 */
public record RideEtaContext(UUID driverId, double pickupLatitude, double pickupLongitude, RideStatus status) {
}