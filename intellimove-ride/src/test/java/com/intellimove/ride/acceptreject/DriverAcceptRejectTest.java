package com.intellimove.ride.acceptreject;

import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.InvalidStateTransitionException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.ride.RideServiceApplication;
import com.intellimove.ride.dto.CreateRideRequest;
import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.service.RideService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Driver ride-request acceptance & rejection tests (Uber-style flow):
 * - Eligible (assigned) driver accepts a DRIVER_ASSIGNED ride
 * - Ineligible / wrong / unassigned drivers are rejected
 * - Rejection returns the ride to REQUESTED (reassignment, NOT cancellation)
 * - Concurrent acceptance: exactly one driver can win
 * - Existing state-machine protection remains intact
 *
 * NOTE on role checks: 401/403 enforcement (customer calling driver endpoints,
 * unauthenticated access) happens at the web layer via @PreAuthorize and the
 * gateway JWT filter and is covered by the Playwright/E2E suites; these tests
 * exercise the service-level authorization contracts.
 */
@SpringBootTest(classes = RideServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DriverAcceptRejectTest {

    @Autowired
    private RideService rideService;

    private UUID customer;
    private UUID driverA;
    private UUID driverB;

    @BeforeEach
    void setUp() {
        customer = UUID.randomUUID();
        driverA = UUID.randomUUID();
        driverB = UUID.randomUUID();
    }

    private CreateRideRequest createDefaultRequest() {
        return CreateRideRequest.builder()
                .rideType(RideType.ECONOMY)
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .pickupAddress("123 Main St")
                .dropoffAddress("Times Square")
                .build();
    }

    private RideResponse assignedRide() {
        RideResponse ride = rideService.requestRide(customer, createDefaultRequest());
        return rideService.assignDriver(ride.getId(), driverA);
    }

    // ── Acceptance ────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Accept: eligible (assigned) driver accepts ride -> DRIVER_ACCEPTED")
    void testEligibleDriverAccepts() {
        RideResponse ride = assignedRide();
        assertEquals(RideStatus.DRIVER_ASSIGNED, ride.getStatus());

        RideResponse accepted = rideService.driverAccept(ride.getId(), driverA);

        assertEquals(RideStatus.DRIVER_ACCEPTED, accepted.getStatus());
        assertEquals(driverA, accepted.getDriverId());
        assertNotNull(accepted.getDriverAcceptedAt());
    }

    @Test
    @Order(2)
    @DisplayName("Accept: ineligible driver (no assignment) is rejected")
    void testIneligibleDriverRejected() {
        RideResponse ride = rideService.requestRide(customer, createDefaultRequest());
        final UUID rideId = ride.getId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rideService.driverAccept(rideId, driverA));
        assertEquals("NOT_ASSIGNED", ex.getErrorCode());
    }

    @Test
    @Order(3)
    @DisplayName("Accept: caller that is not a driver (e.g. customer) cannot accept")
    void testCustomerCannotAccept() {
        RideResponse ride = assignedRide();
        final UUID rideId = ride.getId();

        // Service level: the customer is not the assigned driver.
        BusinessException ex = assertThrows(BusinessException.class,
                () -> rideService.driverAccept(rideId, customer));
        assertEquals("NOT_ASSIGNED", ex.getErrorCode());
        // Web level: @PreAuthorize("hasRole('DRIVER')") blocks CUSTOMER roles with 403
        // and the gateway rejects unauthenticated requests with 401 (E2E-verified).
    }

    @Test
    @Order(4)
    @DisplayName("Accept: wrong driver cannot accept another driver's ride")
    void testWrongDriverRejected() {
        RideResponse ride = assignedRide();
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> rideService.driverAccept(rideId, driverB));
        // The ride must be untouched.
        assertEquals(RideStatus.DRIVER_ASSIGNED, rideService.getRide(rideId).getStatus());
    }

    // ── Concurrency & conflict ────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Concurrency: two drivers accept simultaneously -> exactly one succeeds")
    void testConcurrentAcceptanceExactlyOneWinner() throws Exception {
        RideResponse ride = assignedRide();
        final UUID rideId = ride.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // Driver A is the assigned driver; driver B is not. B can NEVER win.
        for (UUID driver : new UUID[]{driverA, driverB}) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    rideService.driverAccept(rideId, driver);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // expected for the ineligible driver / losing racer
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one driver may win the acceptance race");
        RideResponse result = rideService.getRide(rideId);
        assertEquals(RideStatus.DRIVER_ACCEPTED, result.getStatus());
        assertEquals(driverA, result.getDriverId(), "Only the assigned driver can be the winner");
    }

    @Test
    @Order(6)
    @DisplayName("Conflict: accepting an already-accepted ride is rejected")
    void testAlreadyAcceptedRideConflict() {
        RideResponse ride = assignedRide();
        rideService.driverAccept(ride.getId(), driverA);
        final UUID rideId = ride.getId();

        assertThrows(InvalidStateTransitionException.class,
                () -> rideService.driverAccept(rideId, driverA));
    }

    // ── Rejection & reassignment ──────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Reject: assigned driver rejects -> ride returns to REQUESTED, driver cleared")
    void testDriverRejectReturnsToRequested() {
        RideResponse ride = assignedRide();

        RideResponse rejected = rideService.driverReject(ride.getId(), driverA);

        assertEquals(RideStatus.REQUESTED, rejected.getStatus(),
                "Rejection must NOT cancel the ride — it returns to REQUESTED for reassignment");
        assertNull(rejected.getDriverId(), "The rejecting driver must be unassigned");
        assertNull(rejected.getDriverAssignedAt());
    }

    @Test
    @Order(8)
    @DisplayName("Reject: ride can be re-assigned to another driver after rejection")
    void testReassignmentAfterRejection() {
        RideResponse ride = assignedRide();
        rideService.driverReject(ride.getId(), driverA);

        RideResponse reassigned = rideService.assignDriver(ride.getId(), driverB);
        assertEquals(RideStatus.DRIVER_ASSIGNED, reassigned.getStatus());
        assertEquals(driverB, reassigned.getDriverId());

        // The new driver can now accept.
        RideResponse accepted = rideService.driverAccept(ride.getId(), driverB);
        assertEquals(RideStatus.DRIVER_ACCEPTED, accepted.getStatus());
    }

    @Test
    @Order(9)
    @DisplayName("Reject: wrong driver cannot reject another driver's ride")
    void testWrongDriverCannotReject() {
        RideResponse ride = assignedRide();
        final UUID rideId = ride.getId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rideService.driverReject(rideId, driverB));
        assertEquals("NOT_ASSIGNED", ex.getErrorCode());
        assertEquals(RideStatus.DRIVER_ASSIGNED, rideService.getRide(rideId).getStatus());
    }

    @Test
    @Order(10)
    @DisplayName("Reject: cannot reject a ride without an assignment")
    void testCannotRejectUnassignedRide() {
        RideResponse ride = rideService.requestRide(customer, createDefaultRequest());
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> rideService.driverReject(rideId, driverA));
    }

    // ── Invalid input & state-machine protection ──────────────────────────

    @Test
    @Order(11)
    @DisplayName("Invalid: accept/reject with non-existent ride ID -> ResourceNotFoundException")
    void testInvalidRideId() {
        assertThrows(ResourceNotFoundException.class,
                () -> rideService.driverAccept(UUID.randomUUID(), driverA));
        assertThrows(ResourceNotFoundException.class,
                () -> rideService.driverReject(UUID.randomUUID(), driverA));
    }

    @Test
    @Order(12)
    @DisplayName("State machine: cannot accept from REQUESTED without assignment")
    void testAcceptFromRequestedStillBlocked() {
        RideResponse ride = rideService.requestRide(customer, createDefaultRequest());
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> rideService.driverAccept(rideId, driverA));
    }

    @Test
    @Order(13)
    @DisplayName("State machine: cannot skip states after assignment (start before accept blocked)")
    void testStartBeforeAcceptStillBlocked() {
        RideResponse ride = assignedRide();
        final UUID rideId = ride.getId();

        assertThrows(InvalidStateTransitionException.class, () -> rideService.startTrip(rideId, driverA));
    }

    @Test
    @Order(14)
    @DisplayName("State machine: rejection is only allowed while DRIVER_ASSIGNED")
    void testRejectOnlyFromAssignedState() {
        RideResponse ride = assignedRide();
        rideService.driverAccept(ride.getId(), driverA);
        final UUID rideId = ride.getId();

        // DRIVER_ACCEPTED -> REQUESTED is not a valid transition.
        assertThrows(InvalidStateTransitionException.class,
                () -> rideService.driverReject(rideId, driverA));

        // Terminal rides can never be rejected back to REQUESTED.
        rideService.startTrip(rideId, driverA);
        rideService.completeTrip(rideId, driverA);
        assertThrows(InvalidStateTransitionException.class,
                () -> rideService.driverReject(rideId, driverA));
    }
}
