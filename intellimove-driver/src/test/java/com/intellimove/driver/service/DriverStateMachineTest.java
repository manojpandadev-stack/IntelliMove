package com.intellimove.driver.service;

import com.intellimove.common.enums.DriverStatus;
import com.intellimove.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DriverStateMachineTest {

    // Replicate the same transition map from DriverService
    private static final Map<DriverStatus, Set<DriverStatus>> VALID_TRANSITIONS = Map.of(
            DriverStatus.OFFLINE, Set.of(DriverStatus.ONLINE, DriverStatus.SUSPENDED),
            DriverStatus.ONLINE, Set.of(DriverStatus.AVAILABLE, DriverStatus.OFFLINE, DriverStatus.SUSPENDED),
            DriverStatus.AVAILABLE, Set.of(DriverStatus.OFFERED, DriverStatus.OFFLINE, DriverStatus.ONLINE, DriverStatus.SUSPENDED),
            DriverStatus.OFFERED, Set.of(DriverStatus.ON_TRIP, DriverStatus.AVAILABLE, DriverStatus.ONLINE),
            DriverStatus.ON_TRIP, Set.of(DriverStatus.AVAILABLE, DriverStatus.ONLINE),
            DriverStatus.SUSPENDED, Set.of(DriverStatus.OFFLINE)
    );

    private boolean canTransition(DriverStatus from, DriverStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    @Test
    void testOfflineToOnline() {
        assertTrue(canTransition(DriverStatus.OFFLINE, DriverStatus.ONLINE));
    }

    @Test
    void testOnlineToAvailable() {
        assertTrue(canTransition(DriverStatus.ONLINE, DriverStatus.AVAILABLE));
    }

    @Test
    void testAvailableToOffered() {
        assertTrue(canTransition(DriverStatus.AVAILABLE, DriverStatus.OFFERED));
    }

    @Test
    void testOfferedToOnTrip() {
        assertTrue(canTransition(DriverStatus.OFFERED, DriverStatus.ON_TRIP));
    }

    @Test
    void testOnTripToAvailable() {
        assertTrue(canTransition(DriverStatus.ON_TRIP, DriverStatus.AVAILABLE));
    }

    @Test
    void testOnTripToOnline() {
        assertTrue(canTransition(DriverStatus.ON_TRIP, DriverStatus.ONLINE));
    }

    @Test
    void testSuspendedToOffline() {
        assertTrue(canTransition(DriverStatus.SUSPENDED, DriverStatus.OFFLINE));
    }

    // Invalid transitions
    @Test
    void testOfflineToAvailable_Invalid() {
        assertFalse(canTransition(DriverStatus.OFFLINE, DriverStatus.AVAILABLE));
    }

    @Test
    void testOfflineToOnTrip_Invalid() {
        assertFalse(canTransition(DriverStatus.OFFLINE, DriverStatus.ON_TRIP));
    }

    @Test
    void testAvailableToOnline_Valid() {
        assertTrue(canTransition(DriverStatus.AVAILABLE, DriverStatus.ONLINE));
    }

    @Test
    void testSuspendedToOnline_Invalid() {
        assertFalse(canTransition(DriverStatus.SUSPENDED, DriverStatus.ONLINE));
    }

    @Test
    void testTerminalStateOnTripToOffered_Invalid() {
        assertFalse(canTransition(DriverStatus.ON_TRIP, DriverStatus.OFFERED));
    }
}
