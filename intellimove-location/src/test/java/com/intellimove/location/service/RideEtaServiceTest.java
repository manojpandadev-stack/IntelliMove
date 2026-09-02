package com.intellimove.location.service;

import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.location.dto.RideEtaContext;
import com.intellimove.location.dto.RideEtaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for live pickup ETA computation: haversine distance math, the
 * documented speed assumption, staleness handling, and every "no ETA" branch
 * (no driver, missing location, inactive ride, unknown ride).
 */
class RideEtaServiceTest {

    private final DriverLocationService driverLocationService = mock(DriverLocationService.class);
    private final RideValidationService rideValidationService = mock(RideValidationService.class);

    private RideEtaService newService(double speedKmh) {
        return new RideEtaService(driverLocationService, rideValidationService, speedKmh);
    }

    private DriverLocationService.DriverLocation location(double lat, double lng, long updatedAtMs) {
        return new DriverLocationService.DriverLocation("driver-1", lat, lng, 0,
                Map.of("updatedAt", String.valueOf(updatedAtMs)));
    }

    private void stubContext(String driverId, double pickupLat, double pickupLng, String status) {
        when(rideValidationService.getRideEtaContext(anyString()))
                .thenReturn(Optional.of(new RideEtaContext(driverId, pickupLat, pickupLng, status)));
    }
@Test
    @DisplayName("Valid active ride + fresh live location returns ETA and distance")
    void computesEtaForActiveRide() {
        RideEtaService service = newService(25.0);
                UUID rideId = UUID.randomUUID();
                // Driver ~0.3 km from the pickup (coordinates chosen so the rounded
        // 1-decimal distanceKm is non-zero and clearly < 0.5 km).
        stubContext("driver-1", 40.712800, -74.006000, "DRIVER_ASSIGNED");
        when(driverLocationService.getDriverLocation("driver-1"))
                .thenReturn(Optional.of(location(40.713000, -74.008000, System.currentTimeMillis())));

        RideEtaResponse eta = service.computeEta(rideId);

        assertEquals(1, eta.etaMinutes(), "~0.3 km at 25 km/h is ~1 min, minimum 1");
        assertTrue(eta.distanceKm() >= 0.1 && eta.distanceKm() < 0.5, "distance should be real and small");
        assertEquals(RideEtaService.SOURCE, eta.source());
        assertNotNull(eta.calculatedAt());
    }

    @Test
    @DisplayName("ETA math: farther driver yields proportionally larger minutes")
    void etaScalesWithDistance() {
        RideEtaService service = newService(25.0);
        UUID rideId = UUID.randomUUID();
        // Drive ~5.5 km north at 25 km/h → ~13.2 min → rounded up to 14.
        stubContext("driver-1", 40.712800, -74.006000, "DRIVER_ACCEPTED");
        when(driverLocationService.getDriverLocation("driver-1"))
                .thenReturn(Optional.of(location(40.762500, -74.006000, System.currentTimeMillis())));

        RideEtaResponse eta = service.computeEta(rideId);

        assertEquals(14, eta.etaMinutes());
        assertTrue(eta.distanceKm() > 5.0 && eta.distanceKm() < 6.0, "actual haversine distance ~5.53 km");
    }

    @Test
    @DisplayName("ETA math: slower speed assumption increases minutes")
    void slowerSpeedIncreasesEta() {
        RideEtaService service = newService(12.5); // half of default
        UUID rideId = UUID.randomUUID();
        stubContext("driver-1", 40.712800, -74.006000, "DRIVER_ARRIVING");
        when(driverLocationService.getDriverLocation("driver-1"))
                .thenReturn(Optional.of(location(40.762500, -74.006000, System.currentTimeMillis())));

        RideEtaResponse eta = service.computeEta(rideId);

        assertEquals(27, eta.etaMinutes(), "5.53 km at 12.5 km/h = 26.5 min → 27");
    }

    @Test
    @DisplayName("Unknown ride → ResourceNotFoundException")
    void unknownRideFails() {
        when(rideValidationService.getRideEtaContext(anyString())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> newService(25.0).computeEta(UUID.randomUUID()));
    }

    @Test
    @DisplayName("No driver assigned → IllegalStateException")
    void noDriverFails() {
        stubContext(null, 40.7128, -74.0060, "DRIVER_ASSIGNED");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> newService(25.0).computeEta(UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("No driver"));
    }

    @Test
    @DisplayName("Completed ride → IllegalStateException")
    void completedRideFails() {
        stubContext("driver-1", 40.7128, -74.0060, "TRIP_COMPLETED");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> newService(25.0).computeEta(UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("no longer active"));
    }

    @Test
    @DisplayName("Cancelled ride → IllegalStateException")
    void cancelledRideFails() {
        stubContext("driver-1", 40.7128, -74.0060, "CANCELLED");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> newService(25.0).computeEta(UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("no longer active"));
    }

    @Test
    @DisplayName("Ride still requesting (no pre-trip driver) → IllegalStateException")
    void requestedRideFails() {
        stubContext(null, 40.7128, -74.0060, "REQUESTED");

        assertThrows(IllegalStateException.class, () -> newService(25.0).computeEta(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Missing driver location → ResourceNotFoundException")
    void missingLocationFails() {
        stubContext("driver-1", 40.7128, -74.0060, "DRIVER_ASSIGNED");
        when(driverLocationService.getDriverLocation("driver-1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> newService(25.0).computeEta(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Stale driver location → IllegalStateException")
    void staleLocationFails() {
        stubContext("driver-1", 40.7128, -74.0060, "DRIVER_ASSIGNED");
        long stale = System.currentTimeMillis() - (6 * 60 * 1000L); // 6 minutes old
        when(driverLocationService.getDriverLocation("driver-1"))
                .thenReturn(Optional.of(location(40.7130, -74.0055, stale)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> newService(25.0).computeEta(UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("stale"));
    }

    @Test
    @DisplayName("Location without heartbeat metadata treated as unavailable")
    void locationWithoutHeartbeatFails() {
        stubContext("driver-1", 40.7128, -74.0060, "DRIVER_ASSIGNED");
        when(driverLocationService.getDriverLocation("driver-1"))
                .thenReturn(Optional.of(new DriverLocationService.DriverLocation(
                        "driver-1", 40.7130, -74.0055, 0, Map.of())));

        assertThrows(IllegalStateException.class, () -> newService(25.0).computeEta(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Haversine: same point → zero, and symmetric")
    void haversineMath() {
        assertEquals(0.0, RideEtaService.haversineKm(40.7128, -74.0060, 40.7128, -74.0060), 1e-9);
        double a = RideEtaService.haversineKm(40.7128, -74.0060, 48.8566, 2.3522);
        double b = RideEtaService.haversineKm(48.8566, 2.3522, 40.7128, -74.0060);
        assertEquals(a, b, 1e-9, "haversine must be symmetric");
        assertTrue(a > 5000, "NYC→Paris is ~5800 km");
    }
}