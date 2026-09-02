package com.intellimove.location.dto;

import java.time.Instant;

/**
 * Live pickup ETA computed from the assigned driver's real Redis-GEO position
 * and the ride pickup coordinates. Every value is derived from real data:
 * {@code distanceKm} is the great-circle distance between the driver's latest
 * reported location and the pickup; {@code etaMinutes} is that distance
 * converted to travel time with the intentionally documented urban speed
 * assumption (see {@code eta.speed-kmh}); {@code calculatedAt} is when the
 * ETA was computed; {@code source} identifies the calculation as live.
 */
public record RideEtaResponse(int etaMinutes, double distanceKm, Instant calculatedAt, String source) {
}