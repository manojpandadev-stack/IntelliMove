package com.intellimove.location.dto;

/**
 * Location-side view of the ride context supplied by the Ride Service's
 * internal {@code /eta-context} endpoint. {@code driverId} is the assigned
 * driver's user ID (the Redis GEO member contract), coordinates are the ride
 * pickup, {@code status} is the current ride state as a string.
 */
public record RideEtaContext(String driverId, double pickupLatitude, double pickupLongitude, String status) {
}