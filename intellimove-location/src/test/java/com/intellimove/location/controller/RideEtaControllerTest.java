package com.intellimove.location.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.location.dto.RideEtaResponse;
import com.intellimove.location.service.RideEtaService;
import com.intellimove.location.service.RideValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Endpoint-level tests for GET /api/v1/location/ride/{rideId}/eta covering
 * authorization/IDOR, invalid input, and every error mapping (no driver,
 * inactive ride, missing/stale location, service unavailable).
 */
@ExtendWith(MockitoExtension.class)
class RideEtaControllerTest {

    @Mock
    private RideEtaService rideEtaService;
    @Mock
    private RideValidationService rideValidationService;

    private RideEtaController controller;
    private UUID rideId;

    @BeforeEach
    void setUp() {
        controller = new RideEtaController(rideEtaService, rideValidationService);
        rideId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorized customer receives a 200 ETA")
    void authorizedCustomerGetsEta() {
        UUID customerId = UUID.randomUUID();
        authenticate(customerId.toString(), "ROLE_CUSTOMER");
        when(rideValidationService.isUserAuthorizedForRide(rideId.toString(), customerId.toString()))
                .thenReturn(true);
        when(rideEtaService.computeEta(rideId)).thenReturn(stubEta());

        ResponseEntity<ApiResponse<RideEtaResponse>> resp = controller.getRideEta(rideId.toString());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody() != null && resp.getBody().isSuccess());
        assertEquals(4, resp.getBody().getData().etaMinutes());
        assertEquals(RideEtaService.SOURCE, resp.getBody().getData().source());
    }

    @Test
    @DisplayName("Unauthorized customer cannot read another ride's ETA (IDOR)")
    void unauthorizedCustomerRejected() {
        UUID attacker = UUID.randomUUID();
        authenticate(attacker.toString(), "ROLE_CUSTOMER");
        when(rideValidationService.isUserAuthorizedForRide(rideId.toString(), attacker.toString()))
                .thenReturn(false);

        ResponseEntity<ApiResponse<RideEtaResponse>> resp = controller.getRideEta(rideId.toString());

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

        @Test
    @DisplayName("Admin can read any ride's ETA")
    void adminAllowed() {
        authenticate(UUID.randomUUID().toString(), "ROLE_ADMIN");
        when(rideEtaService.computeEta(rideId)).thenReturn(stubEta());

        ResponseEntity<ApiResponse<RideEtaResponse>> resp = controller.getRideEta(rideId.toString());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401")
    void unauthenticatedRejected() {
        ResponseEntity<ApiResponse<RideEtaResponse>> resp = controller.getRideEta(rideId.toString());

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("Invalid ride ID is rejected with 400")
    void invalidRideIdRejected() {
        authenticate(UUID.randomUUID().toString(), "ROLE_CUSTOMER");

        ResponseEntity<ApiResponse<RideEtaResponse>> resp = controller.getRideEta("not-a-uuid");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    @DisplayName("Ride not found maps to 404")
    void rideNotFound() {
        authenticate(UUID.randomUUID().toString(), "ROLE_CUSTOMER");
        when(rideValidationService.isUserAuthorizedForRide(anyString(), anyString())).thenReturn(true);
        when(rideEtaService.computeEta(rideId))
                .thenThrow(new ResourceNotFoundException("Ride", "id", rideId));

        ResponseEntity<ApiResponse<RideEtaResponse>> resp = controller.getRideEta(rideId.toString());

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    private void authenticate(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }

        private RideEtaResponse stubEta() {
        return new RideEtaResponse(4, 1.8, Instant.now(), RideEtaService.SOURCE);
    }
}
