package com.intellimove.location.service;

import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.location.dto.RideEtaContext;
import com.intellimove.location.dto.RideEtaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Computes a real pickup ETA for an active ride from the assigned driver's
 * latest real-time position (Redis GEO, owned by {@link DriverLocationService})
 * and the ride pickup coordinates.
 *
 * <h2>ETA calculation methodology</h2>
 * {@code distanceKm} is the great-circle (haversine) distance between the
 * driver's latest reported position and the pickup. {@code etaMinutes} =
 * {@code ceil(distanceKm / speedKmh * 60)} using the deliberately documented
 * <strong>urban driving speed assumption</strong> {@code eta.speed-kmh}
 * (default 25 km/h) — the project has no external routing/speed provider. A
 * minimum of 1 minute is returned and everything is labelled
 * {@code source = "LIVE_DRIVER_LOCATION"}. Nothing is ever fabricated: if a
 * driver, a fresh location, or a pre-trip ride state is missing, no ETA is
 * returned.
 */
@Service
@Slf4j
public class RideEtaService {

    public static final String SOURCE = "LIVE_DRIVER_LOCATION";

    /** A location heartbeat older than this is treated as stale/absent. */
    static final long STALE_LOCATION_MS = 5 * 60 * 1000L;

    /** States where a pickup ETA is meaningful (driver heading to pickup). */
    static final Set<RideStatus> ETA_STATUSES = EnumSet.of(
            RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ACCEPTED, RideStatus.DRIVER_ARRIVING);

    private final DriverLocationService driverLocationService;
    private final RideValidationService rideValidationService;
    private final double speedKmh;

    public RideEtaService(DriverLocationService driverLocationService,
                          RideValidationService rideValidationService,
                          @Value("${eta.speed-kmh:25.0}") double speedKmh) {
        this.driverLocationService = driverLocationService;
        this.rideValidationService = rideValidationService;
        this.speedKmh = speedKmh;
    }

    /**
     * Compute a live pickup ETA for the given ride.
     *
     * @throws ResourceNotFoundException when the ride, or the driver's live
     *         location, does not exist (maps to HTTP 404).
     * @throws IllegalStateException when the ride has no driver, is not in a
     *         pre-trip state, or its location is stale (maps to HTTP 409).
     */
    public RideEtaResponse computeEta(UUID rideId) {
        Optional<RideEtaContext> ctxOpt = rideValidationService.getRideEtaContext(rideId.toString());
        RideEtaContext ctx = ctxOpt.orElseThrow(
                () -> new ResourceNotFoundException("Ride", "id", rideId));

        RideStatus status = parseStatus(ctx.status());
        if (!ETA_STATUSES.contains(status)) {
            boolean terminal = status == RideStatus.TRIP_COMPLETED || status == RideStatus.CANCELLED;
            throw new IllegalStateException(
                    terminal ? "Ride is no longer active (" + status + ")"
                             : "Pickup ETA is not available while the ride is " + status);
        }

        if (ctx.driverId() == null || ctx.driverId().isBlank()) {
            throw new IllegalStateException("No driver assigned to this ride yet");
        }

        DriverLocationService.DriverLocation loc = driverLocationService
                .getDriverLocation(ctx.driverId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Driver location", "driverId", ctx.driverId()));

        long lastBeat = updatedAtMs(loc.metadata());
        if (lastBeat == 0L || System.currentTimeMillis() - lastBeat > STALE_LOCATION_MS) {
            throw new IllegalStateException("Driver location is stale or incomplete");
        }

        double distKm = haversineKm(
                loc.latitude(), loc.longitude(), ctx.pickupLatitude(), ctx.pickupLongitude());
        int etaMinutes = (int) Math.max(1L, Math.ceil((distKm / speedKmh) * 60.0));

        return new RideEtaResponse(etaMinutes, Math.round(distKm * 10.0) / 10.0,
                Instant.now(), SOURCE);
    }

    /** Returns the ride status, or {@code null} when unknown/unparseable. */
    private RideStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return RideStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long updatedAtMs(Map<String, String> metadata) {
        if (metadata == null) {
            return 0L;
        }
        String raw = metadata.get("updatedAt");
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Great-circle distance in km between two WGS84 coordinates (haversine). */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}