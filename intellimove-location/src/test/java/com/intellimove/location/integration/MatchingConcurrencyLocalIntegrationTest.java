package com.intellimove.location.integration;

import com.intellimove.location.service.DriverLocationService;
import com.intellimove.location.service.MatchingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auto-dispatch concurrency regression tests (docs/POST_RELEASE_AUDIT.md section 7).
 *
 * Runs against a REAL Redis (compose stack on localhost:6379) because the
 * lock/current_ride/GEO semantics under test are Redis-specific; skipped
 * automatically when no Redis is reachable so CI without infra stays green.
 * The equivalent Testcontainers suite (DriverLocationTestcontainersTest)
 * covers the same invariants on Linux/CI containers.
 *
 * Invariant under test: ONE DRIVER -> AT MOST ONE ACTIVE RIDE.
 */
class MatchingConcurrencyLocalIntegrationTest {

    private static final String GEO_KEY = "driver:locations";
    private static final String DETAILS_PREFIX = "driver:details:";
    private static final String RIDE_PREFIX = "driver:current_ride:";
    private static final String LOCK_PREFIX = "lock:driver:";

    // Remote coordinates keep the 10 km search window free of unrelated
    // drivers that other dev/test activity may have left in the shared GEO set.
    private static final double LAT = -33.8688;
    private static final double LNG = 151.2093;

    private static StringRedisTemplate redis;
    private static boolean redisAvailable;

    private DriverLocationService locationService;
    private MatchingService matchingService;
    private final List<String> driverIds = new ArrayList<>();

    @BeforeAll
    static void connectRedis() {
        try {
            LettuceConnectionFactory factory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration("127.0.0.1", 6379));
            factory.afterPropertiesSet();
            redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            Objects.requireNonNull(redis.getConnectionFactory()).getConnection().close();
            redisAvailable = true;
        } catch (Throwable t) {
            redisAvailable = false;
        }
    }

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable,
                "Redis not reachable on localhost:6379 - skipping local matching integration tests");
        locationService = new DriverLocationService(redis, null);
        matchingService = new MatchingService(locationService, redis);
        ReflectionTestUtils.setField(matchingService, "maxSearchRadiusKm", 10.0);
        ReflectionTestUtils.setField(matchingService, "maxCandidates", 10);
        ReflectionTestUtils.setField(matchingService, "distanceWeight", 0.4);
        ReflectionTestUtils.setField(matchingService, "ratingWeight", 0.3);
        ReflectionTestUtils.setField(matchingService, "tripCountWeight", 0.2);
        ReflectionTestUtils.setField(matchingService, "fairnessWeight", 0.1);
    }

    @AfterEach
    void cleanup() {
        if (!redisAvailable) return;
        for (String id : driverIds) {
            redis.opsForGeo().remove(GEO_KEY, id);
            redis.delete(DETAILS_PREFIX + id);
            redis.delete(RIDE_PREFIX + id);
            redis.delete(LOCK_PREFIX + id);
        }
        driverIds.clear();
    }

    private String registerDriver(String name, Map<String, String> meta) {
        String id = "mctest-" + name + "-" + UUID.randomUUID().toString().substring(0, 8);
        locationService.updateDriverLocation(id, LAT, LNG, meta);
        driverIds.add(id);
        return id;
    }

    @Test
    @DisplayName("TOCTOU: two simultaneous rides must never receive the same driver")
    void twoSimultaneousRidesNeverShareADriver() throws Exception {
        registerDriver("red", Map.of("rating", "5.0", "totalTrips", "100"));
        registerDriver("blue", Map.of("rating", "5.0", "totalTrips", "100"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Optional<MatchingService.MatchResult>> rideA = new AtomicReference<>();
        AtomicReference<Optional<MatchingService.MatchResult>> rideB = new AtomicReference<>();

        Future<?> fa = pool.submit(() -> {
            await(start);
            rideA.set(matchingService.findAndLockDriver("mc-ride-A-" + System.nanoTime(), LAT, LNG, "ECONOMY"));
        });
        Future<?> fb = pool.submit(() -> {
            await(start);
            rideB.set(matchingService.findAndLockDriver("mc-ride-B-" + System.nanoTime(), LAT, LNG, "ECONOMY"));
        });

        start.countDown();
        fa.get(30, TimeUnit.SECONDS);
        fb.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertTrue(rideA.get() != null && rideB.get() != null, "Both matchers must complete");
        // Core invariant: ONE DRIVER -> AT MOST ONE ACTIVE RIDE
        if (rideA.get().isPresent() && rideB.get().isPresent()) {
            assertNotEquals(rideA.get().get().driverId(), rideB.get().get().driverId(),
                    "Two simultaneous rides were assigned the SAME driver");
        }
        assertTrue(rideA.get().isPresent() || rideB.get().isPresent(),
                "At least one ride should acquire a driver");
    }

    @Test
    @DisplayName("TOCTOU: single driver contended by two rides is granted to at most one")
    void singleDriverContendedByTwoRidesGrantedAtMostOnce() throws Exception {
        String only = registerDriver("solo", Map.of("rating", "5.0", "totalTrips", "100"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Optional<MatchingService.MatchResult>> r1 = new AtomicReference<>();
        AtomicReference<Optional<MatchingService.MatchResult>> r2 = new AtomicReference<>();

        Future<?> f1 = pool.submit(() -> {
            await(start);
            r1.set(matchingService.findAndLockDriver("mc-solo-1-" + System.nanoTime(), LAT, LNG, "ECONOMY"));
        });
        Future<?> f2 = pool.submit(() -> {
            await(start);
            r2.set(matchingService.findAndLockDriver("mc-solo-2-" + System.nanoTime(), LAT, LNG, "ECONOMY"));
        });

        start.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        // At most one of the two rides may hold the contested driver
        long grants = Arrays.asList(r1.get(), r2.get()).stream()
                .filter(o -> o != null && o.isPresent())
                .filter(o -> o.get().driverId().equals(only))
                .count();
        assertTrue(grants <= 1,
                "Contested driver was granted to both rides in the same instant (" + grants + ")");
    }

    @Test
    @DisplayName("Exclusion: driver with an active ride is never matched")
    void driverWithActiveRideIsExcluded() {
        String busy = registerDriver("busy", Map.of("rating", "5.0", "totalTrips", "100"));
        String idle = registerDriver("idle", Map.of("rating", "4.0", "totalTrips", "10"));
        redis.opsForValue().set(RIDE_PREFIX + busy, "existing-ride", Duration.ofMinutes(5));

        Optional<MatchingService.MatchResult> result =
                matchingService.findAndLockDriver("mc-busy-ride", LAT, LNG, "ECONOMY");

        assertTrue(result.isPresent(), "Idle driver should be matchable");
        assertNotEquals(busy, result.get().driverId(),
                "Driver with an active ride must be excluded from matching");
    }

    @Test
    @DisplayName("Exclusion: driver holding the distributed lock is never matched")
    void driverHoldingDistributedLockIsExcluded() {
        String locked = registerDriver("locked", Map.of("rating", "5.0", "totalTrips", "100"));
        String free = registerDriver("free", Map.of("rating", "4.0", "totalTrips", "10"));
        redis.opsForValue().set(LOCK_PREFIX + locked, "ride-in-flight", Duration.ofSeconds(10));

        Optional<MatchingService.MatchResult> result =
                matchingService.findAndLockDriver("mc-lock-ride", LAT, LNG, "ECONOMY");

        assertTrue(result.isPresent(), "Free driver should be matchable");
        assertNotEquals(locked, result.get().driverId(),
                "Driver holding the distributed lock must be excluded");
    }

    @Test
    @DisplayName("Exclusion: stale driver (no live heartbeat) is never matched")
    void staleDriverIsExcluded() {
        String fresh = registerDriver("fresh", Map.of("rating", "5.0", "totalTrips", "100"));
        // GEO member WITHOUT a details hash => findNearbyDrivers treats it as
        // stale (heartbeat expired) and evicts it.
        String stale = "mctest-stale-" + UUID.randomUUID().toString().substring(0, 8);
        driverIds.add(stale);
        redis.opsForGeo().add(GEO_KEY, new org.springframework.data.geo.Point(LNG, LAT), stale);

        Optional<MatchingService.MatchResult> result =
                matchingService.findAndLockDriver("mc-stale-ride", LAT, LNG, "ECONOMY");

        assertTrue(result.isPresent(), "Fresh driver should be matchable");
        assertEquals(fresh, result.get().driverId(),
                "Stale driver with no heartbeat data must be excluded");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

