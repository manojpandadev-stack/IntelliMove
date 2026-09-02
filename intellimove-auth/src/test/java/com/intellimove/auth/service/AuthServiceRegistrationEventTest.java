package com.intellimove.auth.service;

import com.intellimove.auth.entity.AuthUser;
import com.intellimove.auth.repository.AuthUserRepository;
import com.intellimove.common.dto.auth.AuthResponse;
import com.intellimove.common.dto.auth.RegisterRequest;
import com.intellimove.common.event.UserRegisteredEvent;
import com.intellimove.common.outbox.OutboxService;
import com.intellimove.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests: a self-registration MUST publish a UserRegisteredEvent
 * through the transactional outbox so the user-service provisions the profile.
 * Without this, GET /api/v1/users/{id} returns 404 for freshly registered users.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRegistrationEventTest {

    private static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private AuthUserRepository authUserRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private OutboxService outboxService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authUserRepository = mock(AuthUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        outboxService = mock(OutboxService.class);
        authService = new AuthService(
                authUserRepository,
                passwordEncoder,
                jwtTokenProvider,
                mock(AuthenticationManager.class),
                mock(StringRedisTemplate.class),
                mock(TransactionTemplate.class),
                outboxService);

        when(authUserRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(inv -> {
            AuthUser u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(TEST_USER_ID);
            }
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpirationMs("access-token")).thenReturn(3_600_000L);
    }

    private AuthResponse registerWithRole(String role) {
        RegisterRequest request = RegisterRequest.builder()
                .email("jane." + role.toLowerCase() + "@test.com")
                .password("password12345")
                .firstName("Jane")
                .lastName("Rider")
                .phoneNumber("+15550001111")
                .role(role)
                .build();
        return authService.register(request);
    }

@Test
    @DisplayName("New CUSTOMER registration publishes UserRegisteredEvent with correct identity")
    void customerRegistrationPublishesProvisioningEvent() {
        AuthResponse response = registerWithRole("CUSTOMER");

        UserRegisteredEvent event = capturePublishedEvent();
        assertNotNull(event);
        assertEquals(TEST_USER_ID.toString(), event.getUserId());
        assertEquals("jane.customer@test.com", event.getEmail());
        assertEquals("Jane", event.getFirstName());
        assertEquals("Rider", event.getLastName());
        assertEquals("+15550001111", event.getPhoneNumber());
        assertEquals("CUSTOMER", event.getRole());
        assertEquals(true, event.isEnabled());
        assertEquals("USER_REGISTERED", event.getEventType());
        assertEquals(response.getUser().getId(), event.getUserId());
    }

    @Test
    @DisplayName("New DRIVER registration publishes UserRegisteredEvent with DRIVER role")
    void driverRegistrationPublishesProvisioningEvent() {
        registerWithRole("DRIVER");

        UserRegisteredEvent event = capturePublishedEvent();
        assertNotNull(event);
        assertEquals("DRIVER", event.getRole());
        assertEquals(TEST_USER_ID.toString(), event.getUserId());
    }

    @Test
    @DisplayName("Registration without a role defaults to CUSTOMER and publishes as such")
    void rolelessRegistrationPublishesCustomerEvent() {
        RegisterRequest request = RegisterRequest.builder()
                .email("nobody@test.com")
                .password("password12345")
                .firstName("No")
                .lastName("Role")
                .build();
        authService.register(request);

                UserRegisteredEvent event = capturePublishedEvent();
        assertNotNull(event);
        assertEquals("CUSTOMER", event.getRole());
    }

    private UserRegisteredEvent capturePublishedEvent() {
        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(outboxService).saveEvent(
                eventCaptor.capture(),
                eq("AuthUser"),
                eq(TEST_USER_ID.toString()),
                eq("user-events"),
                eq(TEST_USER_ID.toString()));
                return eventCaptor.getValue();
    }
}