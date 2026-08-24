package com.intellimove.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Driver matching service with scoring algorithm and distributed locking
 * to prevent race conditions when two riders get the same driver.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final DriverLocationService driverLocationService;
    private final StringRedisTemplate redisTemplate;

    @Value("${matching.max-search-radius-km:10.0}")
    private double maxSearchRadiusKm;

    @Value("${matching.max-candidates:10}")
    private int maxCandidates;

    @Value("${matching.distance-weight:0.4}")
    private double distanceWeight;

    @Value("${matching.rating-weight:0.3}")
    private double ratingWeight;

    @Value("${matching.trip-count-weight:0.2}")
    private double tripCountWeight;

    @Value("${matching.fairness-weight:0.1}")
    private double fairnessWeight;

    private static final String LOCK_PREFIX = "lock:driver:";
    private static final Duration LOCK_DURATION = Duration.ofSeconds(10);

    /**
     * Find and lock the best driver for a ride request.
     * Uses Redis distributed lock to prevent assigning the same driver to two rides.
     */
    public Optional<MatchResult> findAndLockDriver(String rideId, double pickupLat, double pickupLng,
                                                    String rideType) {
        List<DriverLocationService.DriverLocation> nearbyDrivers =
                driverLocationService.findNearbyDrivers(pickupLat, pickupLng, maxSearchRadiusKm);

        if (nearbyDrivers.isEmpty()) {
            log.info("No drivers found near ride {}: ({}, {})", rideId, pickupLat, pickupLng);
            return Optional.empty();
        }

        // Score and rank drivers
        List<ScoredDriver> scored = nearbyDrivers.stream()
                .map(dl -> scoreDriver(dl, pickupLat, pickupLng))
                .sorted(Comparator.comparingDouble(ScoredDriver::score).reversed())
                .limit(maxCandidates)
                .toList();

        // Try to lock drivers in order of score
        for (ScoredDriver scoredDriver : scored) {
            String lockKey = LOCK_PREFIX + scoredDriver.location().driverId();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, rideId, LOCK_DURATION);

            if (Boolean.TRUE.equals(acquired)) {
                log.info("Driver locked for ride {}: driverId={}, score={}",
                        rideId, scoredDriver.location().driverId(), scoredDriver.score());
                return Optional.of(new MatchResult(
                        scoredDriver.location().driverId(),
                        scoredDriver.score(),
                        scoredDriver.location().distanceKm()));
            }
        }

        log.info("All nearby drivers locked/busy for ride {}", rideId);
        return Optional.empty();
    }

    /**
     * Release a driver lock (e.g., if ride was cancelled before driver accepted).
     */
    public void releaseDriverLock(String driverId) {
        String lockKey = LOCK_PREFIX + driverId;
        redisTemplate.delete(lockKey);
        log.debug("Driver lock released: {}", driverId);
    }

    private ScoredDriver scoreDriver(DriverLocationService.DriverLocation dl,
                                      double pickupLat, double pickupLng) {
        // Distance score: closer is better (normalized 0-1, inverse)
        double maxDist = maxSearchRadiusKm;
        double distScore = Math.max(0, 1.0 - (dl.distanceKm() / maxDist));

        // Rating score (normalized 0-1 from 1-5 scale)
        Map<String, String> meta = dl.metadata();
        double rating = Double.parseDouble(meta.getOrDefault("rating", "5.0"));
        double ratingScore = rating / 5.0;

        // Trip count score: more trips = more experienced (log-normalized)
        int tripCount = Integer.parseInt(meta.getOrDefault("totalTrips", "0"));
        double tripScore = Math.min(1.0, Math.log1p(tripCount) / Math.log1p(1000));

        // Fairness: penalize drivers with many recent trips (lower = better)
        int recentTrips = Integer.parseInt(meta.getOrDefault("recentTrips", "0"));
        double fairnessScore = Math.max(0, 1.0 - (recentTrips / 20.0));

        double totalScore = (distanceWeight * distScore)
                + (ratingWeight * ratingScore)
                + (tripCountWeight * tripScore)
                + (fairnessWeight * fairnessScore);

        return new ScoredDriver(dl, totalScore);
    }

    public record ScoredDriver(DriverLocationService.DriverLocation location, double score) {}

    public record MatchResult(String driverId, double score, double distanceKm) {}
}
