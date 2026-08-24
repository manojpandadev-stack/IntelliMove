package com.intellimove.location.integration;

import com.intellimove.location.LocationServiceApplication;
import com.intellimove.location.service.DriverLocationService;
import com.intellimove.location.service.MatchingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testcontainers-based integration tests for driver location tracking and matching
 * using real Redis and Kafka containers. Tests Redis GEO operations, nearby search,
 * distributed locking, and concurrent matching scenarios.
 */
@SpringBootTest(classes = LocationServiceApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DriverLocationTestcontainersTest {

    static {
        WindowsDockerSupport.install();
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.secret", () -> "test-secret-key-for-testing-only-min-32-chars!!");
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("jwt.refresh-expiration-ms", () -> "604800000");
        registry.add("matching.max-search-radius-km", () -> "10.0");
        registry.add("matching.max-candidates", () -> "10");
        registry.add("matching.distance-weight", () -> "0.4");
        registry.add("matching.rating-weight", () -> "0.3");
        registry.add("matching.trip-count-weight", () -> "0.2");
        registry.add("matching.fairness-weight", () -> "0.1");
    }

    @Autowired
    private DriverLocationService driverLocationService;

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redisTemplate.keys("driver:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> lockKeys = redisTemplate.keys("lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Redis GEO: Store driver location")
    void testStoreDriverLocation() {
        String driverId = "driver-001";
        Map<String, String> metadata = Map.of("rating", "4.8", "totalTrips", "150");

        driverLocationService.updateDriverLocation(driverId, 40.7128, -74.0060, metadata);

        Optional<DriverLocationService.DriverLocation> location =
                driverLocationService.getDriverLocation(driverId);
        assertTrue(location.isPresent());
        assertEquals(driverId, location.get().driverId());
        assertEquals(40.7128, location.get().latitude(), 0.001);
        assertEquals(-74.0060, location.get().longitude(), 0.001);
    }

    @Test
    @Order(2)
    @DisplayName("Redis GEO: Nearby search returns correct drivers")
    void testNearbySearch() {
        driverLocationService.updateDriverLocation("driver-near",
                40.7128, -74.0060, Map.of("rating", "5.0", "totalTrips", "200"));
        driverLocationService.updateDriverLocation("driver-far",
                40.8000, -73.9000, Map.of("rating", "4.0", "totalTrips", "50"));

        List<DriverLocationService.DriverLocation> nearby =
                driverLocationService.findNearbyDrivers(40.7128, -74.0060, 1.0);

        assertFalse(nearby.isEmpty(), "Should find at least one nearby driver");
        assertTrue(nearby.stream().anyMatch(d -> d.driverId().equals("driver-near")),
                "Should find the nearby driver");
    }

    @Test
    @Order(3)
    @DisplayName("Redis GEO: Nearby search excludes distant drivers")
    void testNearbySearchExcludesDistantDrivers() {
        driverLocationService.updateDriverLocation("driver-nearby",
                40.7128, -74.0060, Map.of("rating", "5.0", "totalTrips", "200"));
        driverLocationService.updateDriverLocation("driver-very-far",
                41.5000, -73.5000, Map.of("rating", "5.0", "totalTrips", "200"));

        List<DriverLocationService.DriverLocation> nearby =
                driverLocationService.findNearbyDrivers(40.7128, -74.0060, 1.0);

        assertTrue(nearby.stream().noneMatch(d -> d.driverId().equals("driver-very-far")),
                "Very far driver should not appear in 1km radius");
    }

    @Test
    @Order(4)
    @DisplayName("Redis GEO: Multiple drivers sorted by distance")
    void testMultipleDriversSortedByDistance() {
        driverLocationService.updateDriverLocation("driver-closest",
                40.7130, -74.0060, Map.of("rating", "5.0", "totalTrips", "200"));
        driverLocationService.updateDriverLocation("driver-middle",
                40.7200, -74.0100, Map.of("rating", "5.0", "totalTrips", "200"));
        driverLocationService.updateDriverLocation("driver-furthest",
                40.7300, -74.0200, Map.of("rating", "5.0", "totalTrips", "200"));

        List<DriverLocationService.DriverLocation> nearby =
                driverLocationService.findNearbyDrivers(40.7128, -74.0060, 5.0);

        assertEquals(3, nearby.size(), "Should find all 3 drivers");
        for (int i = 0; i < nearby.size() - 1; i++) {
            assertTrue(nearby.get(i).distanceKm() <= nearby.get(i + 1).distanceKm(),
                    "Results should be sorted by distance ascending");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Redis GEO: Active driver count")
    void testActiveDriverCount() {
        assertEquals(0, driverLocationService.getActiveDriverCount());

        driverLocationService.updateDriverLocation("d1", 40.7128, -74.0060, Map.of());
        driverLocationService.updateDriverLocation("d2", 40.7200, -74.0100, Map.of());

        assertEquals(2, driverLocationService.getActiveDriverCount());
    }

    @Test
    @Order(6)
    @DisplayName("Redis GEO: Remove driver location")
    void testRemoveDriverLocation() {
        driverLocationService.updateDriverLocation("driver-remove",
                40.7128, -74.0060, Map.of());

        assertTrue(driverLocationService.getDriverLocation("driver-remove").isPresent());

        driverLocationService.removeDriverLocation("driver-remove");

        assertFalse(driverLocationService.getDriverLocation("driver-remove").isPresent());
    }

    @Test
    @Order(7)
    @DisplayName("Matching: Find and lock best driver")
    void testFindAndLockDriver() {
        driverLocationService.updateDriverLocation("matchable-driver",
                40.7128, -74.0060, Map.of("rating", "4.8", "totalTrips", "150"));

        Optional<MatchingService.MatchResult> result = matchingService.findAndLockDriver(
                "ride-001", 40.7128, -74.0060, "ECONOMY");

        assertTrue(result.isPresent());
        assertEquals("matchable-driver", result.get().driverId());
        assertTrue(result.get().score() > 0);
    }

    @Test
    @Order(8)
    @DisplayName("Matching: No drivers available returns empty")
    void testNoDriversAvailable() {
        Optional<MatchingService.MatchResult> result = matchingService.findAndLockDriver(
                "ride-002", 40.7128, -74.0060, "ECONOMY");

        assertFalse(result.isPresent());
    }

    @Test
    @Order(9)
    @DisplayName("Matching: Distributed lock prevents double assignment")
    void testDistributedLockPreventsDoubleAssignment() {
        driverLocationService.updateDriverLocation("lockable-driver",
                40.7128, -74.0060, Map.of("rating", "5.0", "totalTrips", "100"));

        Optional<MatchingService.MatchResult> first = matchingService.findAndLockDriver(
                "ride-A", 40.7128, -74.0060, "ECONOMY");
        assertTrue(first.isPresent(), "First match should succeed");

        Optional<MatchingService.MatchResult> second = matchingService.findAndLockDriver(
                "ride-B", 40.7128, -74.0060, "ECONOMY");
        assertFalse(second.isPresent(), "Second match should fail - driver is locked");
    }

    @Test
    @Order(10)
    @DisplayName("Matching: Lock release allows re-matching")
    void testLockReleaseAllowsRematching() {
        driverLocationService.updateDriverLocation("release-driver",
                40.7128, -74.0060, Map.of("rating", "5.0", "totalTrips", "100"));

        Optional<MatchingService.MatchResult> first = matchingService.findAndLockDriver(
                "ride-C", 40.7128, -74.0060, "ECONOMY");
        assertTrue(first.isPresent());

        matchingService.releaseDriverLock("release-driver");

        Optional<MatchingService.MatchResult> second = matchingService.findAndLockDriver(
                "ride-D", 40.7128, -74.0060, "ECONOMY");
        assertTrue(second.isPresent(), "After lock release, driver should be matchable again");
    }

    @Test
    @Order(11)
    @DisplayName("Matching: Concurrent matching - only one should succeed")
    void testConcurrentMatching() throws Exception {
        driverLocationService.updateDriverLocation("contest-driver",
                40.7128, -74.0060, Map.of("rating", "5.0", "totalTrips", "100"));

        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String rideId = "ride-concurrent-" + i;
            executor.submit(() -> {
                try {
                    Optional<MatchingService.MatchResult> result = matchingService.findAndLockDriver(
                            rideId, 40.7128, -74.0060, "ECONOMY");
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(),
                "Only one concurrent match should succeed for a single driver");
        assertEquals(threadCount - 1, failureCount.get(),
                "All other concurrent matches should fail");
    }

    @Test
    @Order(12)
    @DisplayName("Matching: Driver scoring - closer driver wins")
    void testDriverScoringCloserWins() {
        driverLocationService.updateDriverLocation("driver-close",
                40.7130, -74.0060, Map.of("rating", "5.0", "totalTrips", "100"));
        driverLocationService.updateDriverLocation("driver-far",
                40.7200, -74.0100, Map.of("rating", "5.0", "totalTrips", "100"));

        Optional<MatchingService.MatchResult> result = matchingService.findAndLockDriver(
                "ride-scoring", 40.7128, -74.0060, "ECONOMY");

        assertTrue(result.isPresent());
        assertEquals("driver-close", result.get().driverId(),
                "Closer driver should be matched first");
    }

    @Test
    @Order(13)
    @DisplayName("Matching: Higher rated driver wins at same distance")
    void testDriverScoringHigherRatingWins() {
        driverLocationService.updateDriverLocation("driver-low-rating",
                40.7128, -74.0060, Map.of("rating", "3.0", "totalTrips", "100"));
        driverLocationService.updateDriverLocation("driver-high-rating",
                40.7128, -74.0060, Map.of("rating", "5.0", "totalTrips", "100"));

        Optional<MatchingService.MatchResult> result = matchingService.findAndLockDriver(
                "ride-rating", 40.7128, -74.0060, "ECONOMY");

        assertTrue(result.isPresent());
        assertEquals("driver-high-rating", result.get().driverId(),
                "Higher rated driver should be matched first at same distance");
    }

    @Test
    @Order(14)
    @DisplayName("Redis GEO: Store ride association with location update")
    void testStoreRideAssociation() {
        driverLocationService.updateDriverLocation("ride-driver",
                40.7128, -74.0060, Map.of(), "ride-123");

        String rideId = driverLocationService.getDriverRideId("ride-driver");
        assertEquals("ride-123", rideId);
    }
}
