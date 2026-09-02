package com.intellimove.user.service;

import com.intellimove.common.enums.Role;
import com.intellimove.common.event.DomainEvent;
import com.intellimove.common.event.RideRequestedEvent;
import com.intellimove.common.event.UserRegisteredEvent;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.entity.User;
import com.intellimove.user.mapper.UserMapper;
import com.intellimove.user.repository.UserProfilePhotoRepository;
import com.intellimove.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for cross-service user provisioning (auth service â†’ outbox â†’
 * Kafka â†’ {this}): registration events must create the user-service profile so
 * GET /api/v1/users/{id} succeeds, duplicates must be idempotent, and retries /
 * concurrent deliveries must be safe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProvisioningTest {

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private UserRepository userRepository;
    private UserMapper userMapper;
    private UserProfilePhotoRepository photoRepository;
    private EntityManager entityManager;
    private jakarta.persistence.Query insertQuery;
    private UserProvisioningWriter provisioningWriter;
    private UserService userService;
    private UserProvisioningConsumer consumer;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(UserMapper.class);
        photoRepository = mock(UserProfilePhotoRepository.class);
        // Real writer backed by the mocked EntityManager/native query so insert
        // semantics (assigned-id INSERT, DataIntegrityViolationException
        // propagation) stay realistic.
        entityManager = mock(EntityManager.class);
        insertQuery = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(insertQuery);
        when(insertQuery.setParameter(anyString(), any())).thenReturn(insertQuery);
        provisioningWriter = new UserProvisioningWriter(entityManager);
        userService = new UserService(userRepository, userMapper, photoRepository, provisioningWriter);
        consumer = new UserProvisioningConsumer(userService);

        when(userMapper.toResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return UserResponse.builder()
                    .id(u.getId())
                    .email(u.getEmail())
                    .firstName(u.getFirstName())
                    .lastName(u.getLastName())
                    .phoneNumber(u.getPhoneNumber())
                    .role(u.getRole())
                    .enabled(u.isEnabled())
                    .build();
        });
    }

    private UserRegisteredEvent registeredEvent(String role) {
        return UserRegisteredEvent.builder()
                .eventType("USER_REGISTERED")
                .userId(USER_ID.toString())
                .email((role != null ? role.toLowerCase() : "customer") + "@test.com")
                .firstName("Jane")
                .lastName("Rider")
                .phoneNumber("+15550001111")
                .role(role)
                .enabled(true)
                .build();
    }

    private Map<String, Object> capturedInsertParams() {
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCap.capture());
        assertTrue(sqlCap.getValue().contains("INSERT INTO users"), "must INSERT INTO users");
        assertTrue(sqlCap.getValue().contains("VALUES (CAST(:id AS uuid)"), "id must be an explicit bound parameter (assigned externally)");
        ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> valCap = ArgumentCaptor.forClass(Object.class);
        verify(insertQuery, atLeast(1)).setParameter(nameCap.capture(), valCap.capture());
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < nameCap.getAllValues().size(); i++) {
            params.put(nameCap.getAllValues().get(i), valCap.getAllValues().get(i));
        }
        return params;
    }

    private User buildProvisionedUser(String email, Role role) {
        User user = User.builder()
                .email(email)
                .firstName("Jane")
                .lastName("Rider")
                .phoneNumber("+15550001111")
                .role(role)
                .enabled(true)
                .build();
        user.setId(USER_ID);
        return user;
    }

    // â”€â”€ 1 & 2. Registration events create the user-service profile â”€â”€

    @Test
    @DisplayName("New CUSTOMER registration event provisions the user-service profile")
    void customerEventProvisionsProfile() {
        consumer.handleUserRegistered(registeredEvent("CUSTOMER"));

        Map<String, Object> params = capturedInsertParams();
        assertEquals(USER_ID.toString(), params.get("id"), "Profile id must equal the auth-service user id");
        assertEquals("customer@test.com", params.get("email"));
        assertEquals("Jane", params.get("firstName"));
        assertEquals("Rider", params.get("lastName"));
        assertEquals("+15550001111", params.get("phoneNumber"));
        assertEquals("CUSTOMER", params.get("role"));
        assertEquals(Boolean.TRUE, params.get("enabled"));
        verify(insertQuery, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("New DRIVER registration event provisions the profile with DRIVER role")
    void driverEventProvisionsProfileWithDriverRole() {
        consumer.handleUserRegistered(registeredEvent("DRIVER"));

        Map<String, Object> params = capturedInsertParams();
        assertEquals(USER_ID.toString(), params.get("id"));
        assertEquals("driver@test.com", params.get("email"));
        assertEquals("DRIVER", params.get("role"));
        verify(insertQuery, times(1)).executeUpdate();
    }

    // â”€â”€ 3. Duplicate provisioning events are safe/idempotent â”€â”€

    @Test
    @DisplayName("Duplicate provisioning event is idempotent â€” no second insert")
    void duplicateEventIsIdempotent() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(buildProvisionedUser("customer@test.com", Role.CUSTOMER)));

        consumer.handleUserRegistered(registeredEvent("CUSTOMER"));
        consumer.handleUserRegistered(registeredEvent("CUSTOMER"));

        verify(insertQuery, never()).executeUpdate();
    }

    @Test
    @DisplayName("Email already owned by another profile id is skipped without crashing (no crash-loop)")
    void emailCollisionSkippedSafely() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("customer@test.com")).thenReturn(true);

        assertDoesNotThrow(() -> consumer.handleUserRegistered(registeredEvent("CUSTOMER")));
        verify(insertQuery, never()).executeUpdate();
    }

    @Test
    @DisplayName("Concurrent provisioning race (duplicate delivery commits first) is treated as success")
    void concurrentInsertRaceIsSafe() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("customer@test.com")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .when(insertQuery).executeUpdate();

        assertDoesNotThrow(() -> consumer.handleUserRegistered(registeredEvent("CUSTOMER")));
    }

    // â”€â”€ 4. GET /api/v1/users/{id} data is available after provisioning â”€â”€

    @Test
    @DisplayName("After provisioning, getUserById resolves the profile (GET /api/v1/users/{id} no longer 404)")
    void provisionedProfileIsReadableAfterRegistration() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        consumer.handleUserRegistered(registeredEvent("CUSTOMER"));
        capturedInsertParams(); // the insert happened with the auth-service id
        when(userRepository.findById(USER_ID)).thenReturn(
                Optional.of(buildProvisionedUser("customer@test.com", Role.CUSTOMER)));

        UserResponse response = userService.getUserById(USER_ID);

        assertEquals(USER_ID, response.getId());
        assertEquals("customer@test.com", response.getEmail());
        assertEquals(Role.CUSTOMER, response.getRole());
    }

    // â”€â”€ 5. Existing users are unaffected â”€â”€

    @Test
    @DisplayName("Provisioning never overwrites or deletes an already-existing profile")
    void provisioningDoesNotModifyExistingProfile() {
        User existing = buildProvisionedUser("customer@test.com", Role.CUSTOMER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        consumer.handleUserRegistered(registeredEvent("CUSTOMER"));

        verify(insertQuery, never()).executeUpdate();
        verify(entityManager, never()).remove(any(Object.class));
    }

    // â”€â”€ 6/7. Message-level robustness: retries and bad events â”€â”€

    @Test
    @DisplayName("Transient persistence failure propagates so Kafka redelivers (retry-safe)")
    void transientFailurePropagatesForKafkaRetry() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("customer@test.com")).thenReturn(false);
        doThrow(new RuntimeException("connection refused"))
                .when(insertQuery).executeUpdate();

        assertThrows(RuntimeException.class,
                () -> consumer.handleUserRegistered(registeredEvent("CUSTOMER")));
    }

    @Test
    @DisplayName("Non-registration events on user-events are ignored without touching the repository")
    void nonRegistrationEventsIgnored() {
        RideRequestedEvent rideEvent = RideRequestedEvent.builder()
                .eventType("RIDE_REQUESTED")
                .rideId(UUID.randomUUID().toString())
                .customerId(USER_ID.toString())
                .build();

        assertDoesNotThrow(() -> consumer.handleUserRegistered(rideEvent));
        verify(insertQuery, never()).executeUpdate();
    }

    @Test
    @DisplayName("Malformed events (missing/non-UUID userId, missing names) are skipped, not crashed on")
    void malformedEventsAreSkipped() {
        UserRegisteredEvent noUserId = registeredEvent("CUSTOMER").toBuilder().userId(null).build();
        UserRegisteredEvent badUserId = registeredEvent("CUSTOMER").toBuilder().userId("not-a-uuid").build();
        UserRegisteredEvent missingName = registeredEvent("CUSTOMER").toBuilder().firstName(" ").build();

        assertDoesNotThrow(() -> {
            consumer.handleUserRegistered(noUserId);
            consumer.handleUserRegistered(badUserId);
            consumer.handleUserRegistered(missingName);
        });
        verify(insertQuery, never()).executeUpdate();
    }
}