package com.intellimove.location.security;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.location.controller.LocationController;
import com.intellimove.location.dto.UpdateLocationRequest;
import com.intellimove.location.service.DriverLocationService;
import com.intellimove.location.service.MatchingService;
import com.intellimove.location.service.RideValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression: location GEO keys are the authenticated driver USER ID.
 * Driver profile IDs must be rejected so matching cannot assign an ID
 * that ride accept/start/complete (JWT user ID) will not recognize.
 */
@ExtendWith(MockitoExtension.class)
class LocationIdentityContractTest {

    @Mock
    private DriverLocationService driverLocationService;
    @Mock
    private MatchingService matchingService;
    @Mock
    private RideValidationService rideValidationService;

    private LocationController controller;
    private UUID driverUserId;
    private UUID driverProfileId;

    @BeforeEach
    void setUp() {
        controller = new LocationController(driverLocationService, matchingService, rideValidationService);
        driverUserId = UUID.randomUUID();
        driverProfileId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Canonical /update stores JWT user ID, never a client-supplied profile ID")
    void canonicalUpdateUsesJwtUserId() {
        authenticate(driverUserId, "ROLE_DRIVER");
        UpdateLocationRequest request = locationBody();

        ResponseEntity<ApiResponse<Void>> response = controller.updateMyLocation(request);

        assertEquals(200, response.getStatusCode().value());
        verify(driverLocationService).updateDriverLocation(
                eq(driverUserId.toString()), eq(40.7128), eq(-74.0060), anyMap(), isNull());
    }

    @Test
    @DisplayName("Path update succeeds when path ID equals authenticated driver user ID")
    void pathUpdateAcceptsUserId() {
        authenticate(driverUserId, "ROLE_DRIVER");

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateDriverLocation(driverUserId.toString(), locationBody());

        assertEquals(200, response.getStatusCode().value());
        verify(driverLocationService).updateDriverLocation(
                eq(driverUserId.toString()), anyDouble(), anyDouble(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Path update rejects driver profile ID (IDOR / identity mix-up)")
    void pathUpdateRejectsProfileId() {
        authenticate(driverUserId, "ROLE_DRIVER");

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateDriverLocation(driverProfileId.toString(), locationBody());

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("user ID"));
        verify(driverLocationService, never()).updateDriverLocation(
                anyString(), anyDouble(), anyDouble(), anyMap(), any());
    }

    @Test
    @DisplayName("Path update rejects another driver's user ID")
    void pathUpdateRejectsOtherDriverUserId() {
        authenticate(driverUserId, "ROLE_DRIVER");
        String otherDriverUserId = UUID.randomUUID().toString();

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateDriverLocation(otherDriverUserId, locationBody());

        assertEquals(403, response.getStatusCode().value());
        verify(driverLocationService, never()).updateDriverLocation(
                anyString(), anyDouble(), anyDouble(), anyMap(), any());
    }

    @Test
    @DisplayName("Unauthenticated location update is rejected")
    void unauthenticatedUpdateRejected() {
        ResponseEntity<ApiResponse<Void>> response = controller.updateMyLocation(locationBody());
        assertEquals(401, response.getStatusCode().value());
        verify(driverLocationService, never()).updateDriverLocation(
                anyString(), anyDouble(), anyDouble(), anyMap(), any());
    }

    @Test
    @DisplayName("Driver cannot delete another driver's GEO entry")
    void driverCannotDeleteOtherLocation() {
        authenticate(driverUserId, "ROLE_DRIVER");

        ResponseEntity<ApiResponse<Void>> response =
                controller.removeDriverLocation(UUID.randomUUID().toString());

        assertEquals(403, response.getStatusCode().value());
        verify(driverLocationService, never()).removeDriverLocation(anyString());
    }

    @Test
    @DisplayName("Driver can delete their own GEO entry keyed by user ID")
    void driverCanDeleteOwnLocation() {
        authenticate(driverUserId, "ROLE_DRIVER");

        ResponseEntity<ApiResponse<Void>> response =
                controller.removeDriverLocation(driverUserId.toString());

        assertEquals(200, response.getStatusCode().value());
        verify(driverLocationService).removeDriverLocation(driverUserId.toString());
    }

    @Test
    @DisplayName("Admin can delete any driver's GEO entry")
    void adminCanDeleteAnyLocation() {
        authenticate(UUID.randomUUID(), "ROLE_ADMIN");
        String target = UUID.randomUUID().toString();

        ResponseEntity<ApiResponse<Void>> response = controller.removeDriverLocation(target);

        assertEquals(200, response.getStatusCode().value());
        verify(driverLocationService).removeDriverLocation(target);
    }

    @Test
    @DisplayName("LocationIdentity.matchesAuthenticatedDriver is user-ID based")
    void identityHelper() {
        authenticate(driverUserId, "ROLE_DRIVER");
        assertTrue(LocationIdentity.matchesAuthenticatedDriver(driverUserId.toString()));
        assertFalse(LocationIdentity.matchesAuthenticatedDriver(driverProfileId.toString()));
    }

    private void authenticate(UUID userId, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UpdateLocationRequest locationBody() {
        return UpdateLocationRequest.builder()
                .latitude(40.7128)
                .longitude(-74.0060)
                .metadata(Map.of("rating", "4.8"))
                .build();
    }
}
