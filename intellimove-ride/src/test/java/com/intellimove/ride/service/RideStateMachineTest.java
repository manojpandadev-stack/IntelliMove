package com.intellimove.ride.service;

import com.intellimove.common.enums.RideStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RideStateMachineTest {

    private static final Map<RideStatus, Set<RideStatus>> VALID_TRANSITIONS = Map.of(
            RideStatus.REQUESTED, Set.of(RideStatus.MATCHING, RideStatus.CANCELLED),
            RideStatus.MATCHING, Set.of(RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED),
            RideStatus.DRIVER_ASSIGNED, Set.of(RideStatus.DRIVER_ACCEPTED, RideStatus.CANCELLED),
            RideStatus.DRIVER_ACCEPTED, Set.of(RideStatus.DRIVER_ARRIVING, RideStatus.CANCELLED),
            RideStatus.DRIVER_ARRIVING, Set.of(RideStatus.TRIP_STARTED),
            RideStatus.TRIP_STARTED, Set.of(RideStatus.TRIP_COMPLETED),
            RideStatus.TRIP_COMPLETED, Set.of(),
            RideStatus.CANCELLED, Set.of()
    );

    private boolean canTransition(RideStatus from, RideStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    @Test
    void testFullHappyPath() {
        assertTrue(canTransition(RideStatus.REQUESTED, RideStatus.MATCHING));
        assertTrue(canTransition(RideStatus.MATCHING, RideStatus.DRIVER_ASSIGNED));
        assertTrue(canTransition(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ACCEPTED));
        assertTrue(canTransition(RideStatus.DRIVER_ACCEPTED, RideStatus.DRIVER_ARRIVING));
        assertTrue(canTransition(RideStatus.DRIVER_ARRIVING, RideStatus.TRIP_STARTED));
        assertTrue(canTransition(RideStatus.TRIP_STARTED, RideStatus.TRIP_COMPLETED));
    }

    @Test
    void testCancelledFromRequested() {
        assertTrue(canTransition(RideStatus.REQUESTED, RideStatus.CANCELLED));
    }

    @Test
    void testCancelledFromMatching() {
        assertTrue(canTransition(RideStatus.MATCHING, RideStatus.CANCELLED));
    }

    @Test
    void testCancelledFromDriverAssigned() {
        assertTrue(canTransition(RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED));
    }

    @Test
    void testCancelledFromDriverAccepted() {
        assertTrue(canTransition(RideStatus.DRIVER_ACCEPTED, RideStatus.CANCELLED));
    }

    // Invalid transitions
    @Test
    void testRequestedToTripStarted_Invalid() {
        assertFalse(canTransition(RideStatus.REQUESTED, RideStatus.TRIP_STARTED));
    }

    @Test
    void testCompletedToCancelled_Invalid() {
        assertFalse(canTransition(RideStatus.TRIP_COMPLETED, RideStatus.CANCELLED));
    }

    @Test
    void testCancelledToRequested_Invalid() {
        assertFalse(canTransition(RideStatus.CANCELLED, RideStatus.REQUESTED));
    }

    @Test
    void testDriverArrivingToCancelled_Invalid() {
        assertFalse(canTransition(RideStatus.DRIVER_ARRIVING, RideStatus.CANCELLED));
    }

    @Test
    void testTripStartedToCancelled_Invalid() {
        assertFalse(canTransition(RideStatus.TRIP_STARTED, RideStatus.CANCELLED));
    }

    @Test
    void testTerminalStatesHaveNoOutgoingTransitions() {
        assertTrue(VALID_TRANSITIONS.get(RideStatus.TRIP_COMPLETED).isEmpty());
        assertTrue(VALID_TRANSITIONS.get(RideStatus.CANCELLED).isEmpty());
    }
}
