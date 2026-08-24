package com.intellimove.ride.integration;

import com.intellimove.common.enums.CancellationReason;
import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for RideService using H2 in-memory database.
 * Tests the ride lifecycle state machine, repository operations, and business logic.
 */
@SpringBootTest(classes = RideServiceApplication.class)
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RideServiceIntegrationTest {

    @Autowired
    private RideService rideService;

    @Autowired
    private RideRepository rideRepository;

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
    @DisplayName("Create ride - should set REQUESTED status with estimated fare")
    void testCreateRide() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        assertNotNull(ride);
        assertNotNull(ride.getId());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
        assertEquals(customerId, ride.getCustomerId());
        assertEquals(RideType.ECONOMY, ride.getRideType());
        assertEquals("123 Main St, New York", ride.getPickupAddress());
        assertEquals("Times Square, New York", ride.getDropoffAddress());
        assertNotNull(ride.getEstimatedFare());
        assertTrue(ride.getEstimatedFare().doubleValue() > 0);
        assertEquals("USD", ride.getCurrency());
    }

    @Test
    @Order(2)
    @DisplayName("Full ride lifecycle: REQUESTED -> ASSIGNED -> ACCEPTED -> STARTED -> COMPLETED")
    void testFullRideLifecycle() {
        // Create ride
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());

        // Assign driver
        ride = rideService.assignDriver(ride.getId(), driverId);
        assertEquals(RideStatus.DRIVER_ASSIGNED, ride.getStatus());
        assertEquals(driverId, ride.getDriverId());
        assertNotNull(ride.getDriverAssignedAt());

        // Accept ride
        ride = rideService.driverAccept(ride.getId(), driverId);
        assertEquals(RideStatus.DRIVER_ACCEPTED, ride.getStatus());
        assertNotNull(ride.getDriverAcceptedAt());

        // Start trip
        ride = rideService.startTrip(ride.getId(), driverId);
        assertEquals(RideStatus.TRIP_STARTED, ride.getStatus());
        assertNotNull(ride.getTripStartedAt());

        // Complete trip
        ride = rideService.completeTrip(ride.getId(), driverId);
        assertEquals(RideStatus.TRIP_COMPLETED, ride.getStatus());
        assertNotNull(ride.getTripCompletedAt());
        assertNotNull(ride.getFinalFare());
        assertTrue(ride.getFinalFare().doubleValue() > 0);
    }

    @Test
    @Order(3)
    @DisplayName("Cancel ride from REQUESTED state")
    void testCancelRide() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());

        CancelRideRequest cancelReq = CancelRideRequest.builder()
                .reason(CancellationReason.RIDER_CANCELLED)
                .note("Changed my mind")
                .build();
        ride = rideService.cancelRide(ride.getId(), customerId, cancelReq);
        assertEquals(RideStatus.CANCELLED, ride.getStatus());
        assertEquals(CancellationReason.RIDER_CANCELLED, ride.getCancellationReason());
        assertEquals("CUSTOMER", ride.getCancelledBy());
    }

    @Test
    @Order(4)
    @DisplayName("Invalid state transition should be rejected")
    void testInvalidStateTransitionRejected() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        // Try to start trip without assigning/accepting first
        assertThrows(Exception.class, () -> {
            rideService.startTrip(ride.getId(), driverId);
        });

        // Verify ride is still REQUESTED
        Optional<Ride> persisted = rideRepository.findById(ride.getId());
        assertTrue(persisted.isPresent());
        assertEquals(RideStatus.REQUESTED, persisted.get().getStatus());
    }

    @Test
    @Order(5)
    @DisplayName("Repository persistence and retrieval")
    void testRideRepositoryPersistence() {
        RideResponse ride = rideService.requestRide(customerId, createDefaultRequest());

        Optional<Ride> found = rideRepository.findById(ride.getId());
        assertTrue(found.isPresent());
        assertEquals(RideStatus.REQUESTED, found.get().getStatus());
        assertEquals(customerId, found.get().getCustomerId());
        assertEquals(RideType.ECONOMY, found.get().getRideType());
    }

    @Test
    @Order(6)
    @DisplayName("Premium fare should be higher than economy")
    void testFareEstimationDifferentTypes() {
        RideResponse economyRide = rideService.requestRide(customerId, createDefaultRequest());

        UUID anotherCustomer = UUID.randomUUID();
        CreateRideRequest premiumRequest = CreateRideRequest.builder()
                .rideType(RideType.PREMIUM)
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .pickupAddress("123 Main St")
                .dropoffAddress("Times Square")
                .build();
        RideResponse premiumRide = rideService.requestRide(anotherCustomer, premiumRequest);

        assertTrue(premiumRide.getEstimatedFare().doubleValue() > economyRide.getEstimatedFare().doubleValue(),
                "Premium fare should be higher than economy");
    }

    @Test
    @Order(7)
    @DisplayName("Duplicate active ride should be rejected")
    void testDuplicateActiveRideRejected() {
        rideService.requestRide(customerId, createDefaultRequest());

        // Second ride for same customer should be rejected
        assertThrows(Exception.class, () -> {
            rideService.requestRide(customerId, createDefaultRequest());
        });
    }
}
