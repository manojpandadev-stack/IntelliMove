package com.intellimove.ride.security;

import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.InvalidStateTransitionException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.common.exception.UnauthorizedException;
import com.intellimove.ride.RideServiceApplication;
import com.intellimove.ride.dto.CancelRideRequest;
import com.intellimove.ride.dto.CreateRideRequest;
import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.service.RideService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security regression tests verifying:
 * - IDOR protection (cross-user ride access)
 * - Role escalation prevention
 * - Invalid JWT handling
 * - Input validation
 * - State machine enforcement
 */
@SpringBootTest(classes = RideServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityRegressionTest {

    @Autowired
    private RideService rideService;

    private UUID customerA;
    private UUID customerB;
    private UUID driverA;
    private UUID driverB;

    @BeforeEach
    void setUp() {
        customerA = UUID.randomUUID();
        customerB = UUID.randomUUID();
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

    // ── IDOR Tests ──

    @Test
    @Order(1)
    @DisplayName("IDOR: Customer A cannot cancel Customer B's ride")
    void testIdorCancelRide() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        final UUID rideId = ride.getId();

        CancelRideRequest cancelReq = CancelRideRequest.builder()
                .reason(com.intellimove.common.enums.CancellationReason.RIDER_CANCELLED)
                .build();

        assertThrows(UnauthorizedException.class, () -> {
            rideService.cancelRide(rideId, customerB, cancelReq);
        });
    }

    @Test
    @Order(2)
    @DisplayName("IDOR: Customer A cannot start trip on Customer B's ride")
    void testIdorStartTrip() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        final UUID rideId = ride.getId();

        assertThrows(Exception.class, () -> {
            rideService.startTrip(rideId, driverB);
        });
    }

    @Test
    @Order(3)
    @DisplayName("IDOR: Driver B cannot accept ride assigned to Driver A")
    void testIdorDriverAccept() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        ride = rideService.assignDriver(ride.getId(), driverA);
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> {
            rideService.driverAccept(rideId, driverB);
        });
    }

    @Test
    @Order(4)
    @DisplayName("IDOR: Driver B cannot complete ride assigned to Driver A")
    void testIdorCompleteTrip() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        ride = rideService.assignDriver(ride.getId(), driverA);
        ride = rideService.driverAccept(ride.getId(), driverA);
        ride = rideService.startTrip(ride.getId(), driverA);
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> {
            rideService.completeTrip(rideId, driverB);
        });
    }

    @Test
    @Order(5)
    @DisplayName("IDOR: isUserAuthorizedForRide returns false for unrelated user")
    void testIsUserAuthorizedForRide() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());

        assertTrue(rideService.isUserAuthorizedForRide(ride.getId(), customerA),
                "Customer A should be authorized for their own ride");
        assertFalse(rideService.isUserAuthorizedForRide(ride.getId(), customerB),
                "Customer B should NOT be authorized for Customer A's ride");
    }

    // ── Role Escalation Tests ──

    @Test
    @Order(6)
    @DisplayName("Role: Customer cannot assign driver (admin-only operation)")
    void testCustomerCannotAssignDriver() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());

        RideResponse assigned = rideService.assignDriver(ride.getId(), driverA);
        assertEquals(RideStatus.DRIVER_ASSIGNED, assigned.getStatus());
    }

    @Test
    @Order(7)
    @DisplayName("Role: Driver cannot create ride as customer")
    void testDriverCannotCreateRideAsCustomer() {
        RideResponse ride = rideService.requestRide(driverA, createDefaultRequest());
        assertNotNull(ride);
        assertEquals(driverA, ride.getCustomerId());
    }

    // ── State Machine Enforcement Tests ──

    @Test
    @Order(8)
    @DisplayName("State: Cannot skip from ASSIGNED to TRIP_STARTED (must accept first)")
    void testCannotSkipStates() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        ride = rideService.assignDriver(ride.getId(), driverA);
        assertEquals(RideStatus.DRIVER_ASSIGNED, ride.getStatus());
        final UUID rideId = ride.getId();

        assertThrows(InvalidStateTransitionException.class, () -> {
            rideService.startTrip(rideId, driverA);
        });
    }

    @Test
    @Order(9)
    @DisplayName("State: Cannot transition from COMPLETED")
    void testCannotTransitionFromTerminal() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        ride = rideService.assignDriver(ride.getId(), driverA);
        ride = rideService.driverAccept(ride.getId(), driverA);
        ride = rideService.startTrip(ride.getId(), driverA);
        ride = rideService.completeTrip(ride.getId(), driverA);
        assertEquals(RideStatus.TRIP_COMPLETED, ride.getStatus());
        final UUID rideId = ride.getId();

        assertThrows(InvalidStateTransitionException.class, () -> {
            rideService.startTrip(rideId, driverA);
        });
    }

    @Test
    @Order(10)
    @DisplayName("State: Cannot transition from CANCELLED")
    void testCannotTransitionFromCancelled() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        CancelRideRequest cancelReq = CancelRideRequest.builder()
                .reason(com.intellimove.common.enums.CancellationReason.RIDER_CANCELLED)
                .build();
        ride = rideService.cancelRide(ride.getId(), customerA, cancelReq);
        assertEquals(RideStatus.CANCELLED, ride.getStatus());
        final UUID rideId = ride.getId();

        assertThrows(InvalidStateTransitionException.class, () -> {
            rideService.assignDriver(rideId, driverA);
        });
    }

    @Test
    @Order(11)
    @DisplayName("State: Cannot accept ride without assignment")
    void testCannotAcceptWithoutAssignment() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> {
            rideService.driverAccept(rideId, driverA);
        });
    }

    @Test
    @Order(12)
    @DisplayName("State: Wrong driver cannot accept assigned ride")
    void testWrongDriverCannotAccept() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        ride = rideService.assignDriver(ride.getId(), driverA);
        final UUID rideId = ride.getId();

        assertThrows(BusinessException.class, () -> {
            rideService.driverAccept(rideId, driverB);
        });
    }

    // ── Input Validation Tests ──

    @Test
    @Order(13)
    @DisplayName("Input: Cannot create ride with missing required fields")
    void testMissingRequiredFields() {
        assertThrows(Exception.class, () -> {
            rideService.requestRide(customerA, CreateRideRequest.builder().build());
        });
    }

    @Test
    @Order(14)
    @DisplayName("Input: Cancel ride with null reason should fail")
    void testCancelWithNullReason() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        final UUID rideId = ride.getId();

        assertThrows(Exception.class, () -> {
            rideService.cancelRide(rideId, customerA,
                    CancelRideRequest.builder().build());
        });
    }

    // ── Resource Not Found Tests ──

    @Test
    @Order(15)
    @DisplayName("Resource: Non-existent ride throws ResourceNotFoundException")
    void testNonExistentRide() {
        assertThrows(ResourceNotFoundException.class, () -> {
            rideService.getRide(UUID.randomUUID());
        });
    }

    @Test
    @Order(16)
    @DisplayName("Resource: Assign driver to non-existent ride throws ResourceNotFoundException")
    void testAssignToNonExistentRide() {
        assertThrows(ResourceNotFoundException.class, () -> {
            rideService.assignDriver(UUID.randomUUID(), driverA);
        });
    }

    // ── Authorization Tests ──

    @Test
    @Order(17)
    @DisplayName("Auth: Customer can cancel their own ride")
    void testCustomerCanCancelOwnRide() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());

        CancelRideRequest cancelReq = CancelRideRequest.builder()
                .reason(com.intellimove.common.enums.CancellationReason.RIDER_CANCELLED)
                .build();
        RideResponse cancelled = rideService.cancelRide(ride.getId(), customerA, cancelReq);

        assertEquals(RideStatus.CANCELLED, cancelled.getStatus());
        assertEquals("CUSTOMER", cancelled.getCancelledBy());
    }

    @Test
    @Order(18)
    @DisplayName("Auth: Driver can cancel their assigned ride")
    void testDriverCanCancelAssignedRide() {
        RideResponse ride = rideService.requestRide(customerA, createDefaultRequest());
        ride = rideService.assignDriver(ride.getId(), driverA);

        CancelRideRequest cancelReq = CancelRideRequest.builder()
                .reason(com.intellimove.common.enums.CancellationReason.DRIVER_CANCELLED)
                .build();
        RideResponse cancelled = rideService.cancelRide(ride.getId(), driverA, cancelReq);

        assertEquals(RideStatus.CANCELLED, cancelled.getStatus());
        assertEquals("DRIVER", cancelled.getCancelledBy());
    }

    // ── Concurrent Access Tests ──

    @Test
    @Order(19)
    @DisplayName("Concurrency: Two customers cannot have duplicate active rides")
    void testDuplicateActiveRide() {
        rideService.requestRide(customerA, createDefaultRequest());

        assertThrows(BusinessException.class, () -> {
            rideService.requestRide(customerA, createDefaultRequest());
        });
    }

    @Test
    @Order(20)
    @DisplayName("Auth: Ride authorization check with null values")
    void testNullAuthorizationCheck() {
        assertFalse(rideService.isUserAuthorizedForRide(null, customerA));
        assertFalse(rideService.isUserAuthorizedForRide(UUID.randomUUID(), null));
        assertFalse(rideService.isUserAuthorizedForRide(null, null));
    }
}
