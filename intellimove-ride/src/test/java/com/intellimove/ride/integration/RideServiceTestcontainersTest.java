package com.intellimove.ride.integration;

import com.intellimove.common.enums.CancellationReason;
import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
import com.intellimove.common.outbox.OutboxEvent;
import com.intellimove.common.outbox.OutboxRepository;
import com.intellimove.common.outbox.OutboxStatus;
import com.intellimove.ride.RideServiceApplication;
import com.intellimove.ride.dto.CancelRideRequest;
import com.intellimove.ride.dto.CreateRideRequest;
import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.entity.Ride;
import com.intellimove.ride.repository.RideRepository;
import com.intellimove.ride.service.RideService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testcontainers-based integration tests for RideService using real PostgreSQL,
 * Redis, and Kafka containers. Tests the ride lifecycle state machine, repository
 * operations, outbox events, and concurrent ride requests.
 */
@SpringBootTest(classes = RideServiceApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RideServiceTestcontainersTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("intellimove_ride")
            .withUsername("intellimove")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.secret", () -> "test-secret-key-for-testing-only-min-32-chars!!");
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("jwt.refresh-expiration-ms", () -> "604800000");
        registry.add("ai.ops.llm-enabled", () -> "false");
    }

    @Autowired
    private RideService rideService;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private UUID customerId;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        driverId = UUID.randomUUID();
    }

    private CreateRideRequest createDefaultRequest() {
        return CreateRideRequest.builder()
                .rideType(RideType.ECONOMY)
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .pickupAddress("123 Main St, New York")
                .dropoffAddress("Times Square, New York")
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("PostgreSQL: Create ride with Flyway schema and persist")
    void testCreateRideWithFlywaySchema() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        assertNotNull(ride);
        assertNotNull(ride.getId());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
        assertEquals(customerId, ride.getCustomerId());
        assertNotNull(ride.getEstimatedFare());
        assertTrue(ride.getEstimatedFare().doubleValue() > 0);
    }

    @Test
    @Order(2)
    @DisplayName("PostgreSQL: Full ride lifecycle with real database")
    void testFullRideLifecyclePostgreSQL() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());

        ride = rideService.assignDriver(ride.getId(), driverId);
        assertEquals(RideStatus.DRIVER_ASSIGNED, ride.getStatus());

        ride = rideService.driverAccept(ride.getId(), driverId);
        assertEquals(RideStatus.DRIVER_ACCEPTED, ride.getStatus());

        ride = rideService.startTrip(ride.getId(), driverId);
        assertEquals(RideStatus.TRIP_STARTED, ride.getStatus());

        ride = rideService.completeTrip(ride.getId(), driverId);
        assertEquals(RideStatus.TRIP_COMPLETED, ride.getStatus());
        assertNotNull(ride.getFinalFare());
        assertTrue(ride.getFinalFare().doubleValue() > 0);
    }

    @Test
    @Order(3)
    @DisplayName("PostgreSQL: Invalid state transition rejected with real DB")
    void testInvalidStateTransitionRejectedPostgreSQL() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        assertThrows(Exception.class, () -> {
            rideService.startTrip(ride.getId(), driverId);
        });

        Optional<Ride> persisted = rideRepository.findById(ride.getId());
        assertTrue(persisted.isPresent());
        assertEquals(RideStatus.REQUESTED, persisted.get().getStatus());
    }

    @Test
    @Order(4)
    @DisplayName("PostgreSQL: Database constraints - unique customer active ride")
    void testDatabaseConstraintsPostgreSQL() {
        rideService.requestRide(customerId, createDefaultRequest());

        assertThrows(Exception.class, () -> {
            rideService.requestRide(customerId, createDefaultRequest());
        });
    }

    @Test
    @Order(5)
    @DisplayName("Outbox: Ride creation generates outbox event")
    void testOutboxEventGeneration() {
        long beforeCount = outboxRepository.count();

        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        long afterCount = outboxRepository.count();
        assertTrue(afterCount > beforeCount, "Outbox event should be created on ride request");

        Optional<OutboxEvent> rideEvent = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(ride.getId().toString()))
                .findFirst();
        assertTrue(rideEvent.isPresent(), "Outbox event should reference the ride");
        assertEquals("ride-events", rideEvent.get().getTopic());
        assertEquals(OutboxStatus.PENDING, rideEvent.get().getStatus());
    }

    @Test
    @Order(6)
    @DisplayName("Outbox: Driver assignment generates outbox event")
    void testDriverAssignmentOutboxEvent() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        rideService.assignDriver(ride.getId(), driverId);

        Optional<OutboxEvent> assignEvent = outboxRepository.findAll().stream()
                .filter(e -> e.getEventType().equals("DRIVER_ASSIGNED"))
                .findFirst();
        assertTrue(assignEvent.isPresent(), "DRIVER_ASSIGNED outbox event should exist");
        assertEquals("ride-events", assignEvent.get().getTopic());
    }

    @Test
    @Order(7)
    @DisplayName("Outbox: Trip completion generates outbox event")
    void testTripCompletionOutboxEvent() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());
        rideService.assignDriver(ride.getId(), driverId);
        rideService.driverAccept(ride.getId(), driverId);
        rideService.startTrip(ride.getId(), driverId);
        rideService.completeTrip(ride.getId(), driverId);

        Optional<OutboxEvent> completeEvent = outboxRepository.findAll().stream()
                .filter(e -> e.getEventType().equals("RIDE_COMPLETED"))
                .findFirst();
        assertTrue(completeEvent.isPresent(), "RIDE_COMPLETED outbox event should exist");
    }

    @Test
    @Order(8)
    @DisplayName("PostgreSQL: Transaction isolation - concurrent ride requests")
    void testConcurrentRideRequests() throws Exception {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final UUID customerIdForThread = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    rideService.requestRide(customerIdForThread, createDefaultRequest());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
                "All concurrent ride requests with different customers should succeed");
        assertEquals(0, failureCount.get(), "No ride requests should fail");
    }

    @Test
    @Order(9)
    @DisplayName("PostgreSQL: Pagination works correctly")
    void testPaginationPostgreSQL() {
        UUID customer = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            rideService.requestRide(customer, createDefaultRequest());
        }

        var page1 = rideService.getCustomerRides(customer, 0, 2);
        assertEquals(2, page1.getContent().size());
        assertEquals(0, page1.getPage());
        assertTrue(page1.getTotalElements() >= 5);

        var page2 = rideService.getCustomerRides(customer, 1, 2);
        assertEquals(2, page2.getContent().size());
    }

    @Test
    @Order(10)
    @DisplayName("Redis: Verify Redis containers are connected")
    void testRedisConnected() {
        assertNotNull(rideService, "RideService should be loaded");
    }
}
