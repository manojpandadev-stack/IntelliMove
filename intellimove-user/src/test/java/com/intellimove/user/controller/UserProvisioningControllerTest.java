package com.intellimove.user.controller;

import com.intellimove.common.enums.Role;
import com.intellimove.common.exception.GlobalExceptionHandler;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the cross-service user provisioning gap (auth-service →
 * transactional outbox → Kafka → user-service): GET /api/v1/users/{id} must
 * return 200 with the provisioned profile for freshly registered users instead
 * of the historical 404. Standalone MockMvc + mocks (no Spring context), same
 * pattern as SavedPlaceControllerTest. HTTP-layer authentication/authorization
 * is enforced by the shared JWT filter chain and verified against the live stack.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProvisioningControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** What UserProvisioningConsumer persists after a USER_REGISTERED event. */
    private UserResponse provisionedProfile() {
        return UserResponse.builder()
                .id(TEST_USER_ID)
                .email("jane.customer@test.com")
                .firstName("Jane")
                .lastName("Rider")
                .phoneNumber("+15550001111")
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} returns 200 with the provisioned profile")
    void getUserAfterProvisioningReturns200() throws Exception {
        when(userService.getUserById(TEST_USER_ID)).thenReturn(provisionedProfile());

        mockMvc.perform(get("/api/v1/users/{id}", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(TEST_USER_ID.toString())))
                .andExpect(jsonPath("$.data.email", is("jane.customer@test.com")))
                .andExpect(jsonPath("$.data.firstName", is("Jane")))
                .andExpect(jsonPath("$.data.lastName", is("Rider")))
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} returns 404 while profile is NOT provisioned (original gap symptom)")
    void getUserBeforeProvisioningReturns404() throws Exception {
        when(userService.getUserById(TEST_USER_ID)).thenThrow(
                new ResourceNotFoundException("User", "id", TEST_USER_ID));

        mockMvc.perform(get("/api/v1/users/{id}", TEST_USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}