package com.intellimove.auth.integration;

import com.intellimove.auth.AuthServiceApplication;
import com.intellimove.auth.entity.AuthUser;
import com.intellimove.auth.repository.AuthUserRepository;
import com.intellimove.common.dto.auth.LoginRequest;
import com.intellimove.common.dto.auth.RefreshTokenRequest;
import com.intellimove.common.dto.auth.RegisterRequest;
import com.intellimove.common.enums.Role;
import com.intellimove.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AuthServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @BeforeEach
    void cleanUp() {
        authUserRepository.deleteAll();
    }

    @Test
    void shouldRegisterNewCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "newcustomer@test.com",
                                    "password": "Password123!",
                                    "firstName": "John",
                                    "lastName": "Doe",
                                    "phoneNumber": "+1234567890",
                                    "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("newcustomer@test.com"))
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"));

        assertThat(authUserRepository.findByEmail("newcustomer@test.com")).isPresent();
    }

    @Test
    void shouldRegisterDriver() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "driver@test.com",
                                    "password": "Password123!",
                                    "firstName": "Jane",
                                    "lastName": "Driver",
                                    "role": "DRIVER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value("DRIVER"));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dup@test.com", "password": "Password123!", "firstName": "A", "lastName": "B"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dup@test.com", "password": "Password456!", "firstName": "C", "lastName": "D"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.errorCode").value("EMAIL_EXISTS"));
    }

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        // Register first
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login@test.com", "password": "Secure123!", "firstName": "A", "lastName": "B"}
                                """))
                .andExpect(status().isCreated());

        // Login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login@test.com", "password": "Secure123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "wrongpw@test.com", "password": "Correct123!", "firstName": "A", "lastName": "B"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "wrongpw@test.com", "password": "WrongPassword!"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectNonExistentEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@test.com", "password": "Any1234!"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRefreshToken() throws Exception {
        // Register
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "refresh@test.com", "password": "Secure123!", "firstName": "A", "lastName": "B"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = extractJsonField(registerResponse, "refreshToken");

        // Refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void shouldLockAccountAfterFiveFailedLogins() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "lock@test.com", "password": "Good1234!", "firstName": "A", "lastName": "B"}
                                """))
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "lock@test.com", "password": "WrongPass!"}
                                    """))
                    .andExpect(status().isUnprocessableEntity());
        }

        // Verify account is locked in the database
        AuthUser lockedUser = authUserRepository.findByEmail("lock@test.com").orElseThrow();
        assertThat(lockedUser.isAccountLocked()).isTrue();
        assertThat(lockedUser.getFailedLoginAttempts()).isGreaterThanOrEqualTo(5);

        // Now try with correct password - should be locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "lock@test.com", "password": "Good1234!"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldLogout() throws Exception {
        // Register and get token
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "logout@test.com", "password": "Secure123!", "firstName": "A", "lastName": "B"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accessToken = extractJsonField(registerResponse, "accessToken");

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private String extractJsonField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx == -1) return "";
        int colonIdx = json.indexOf(":", idx);
        int startQuote = json.indexOf("\"", colonIdx + 1);
        int endQuote = json.indexOf("\"", startQuote + 1);
        return json.substring(startQuote + 1, endQuote);
    }
}
