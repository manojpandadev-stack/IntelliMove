package com.intellimove.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellimove.common.exception.GlobalExceptionHandler;
import com.intellimove.user.entity.SavedPlace;
import com.intellimove.user.repository.SavedPlaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regression tests: Saved Places HTTP 500 on fresh users. Controller re-mapped
// under /api/v1/users/saved-places (gateway user route); PlaceRequest now ignores
// unknown client fields (userId/type) so the real UI payload works. Identity
// from JWT principal via SecurityUtils (IDOR-safe). Standalone MockMvc + mocks.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavedPlaceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private SavedPlaceRepository savedPlaceRepository;
    private static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID OTHER_USER_ID = UUID.fromString("999e4567-e89b-12d3-a456-426614174999");
    private static final UUID PLACE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        savedPlaceRepository = mock(SavedPlaceRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SavedPlaceController(savedPlaceRepository))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    private void authenticateAs(UUID userId) {
        var auth = new TestingAuthenticationToken(userId.toString(), "n/a", "ROLE_CUSTOMER");
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private Map<String, Object> fullFrontendPayload() {
        return Map.of("userId", TEST_USER_ID.toString(), "label", "Home",
                "address", "1 Rose Street", "latitude", 40.7128, "longitude", -74.006, "type", "HOME");
    }

    @Nested
    @DisplayName("Fresh user with zero saved places (the original 500 trigger)")
    class FreshUserScenarios {

        @Test
        @DisplayName("GET list for a fresh user returns 200 + empty list (was HTTP 500)")
        void listForFreshUserReturnsEmptyList() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findByUserIdOrderByCreatedAtAsc(TEST_USER_ID)).thenReturn(java.util.List.of());
            mockMvc.perform(get("/api/v1/users/saved-places")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", is(java.util.List.of())));
        }

        @Test
        @DisplayName("PUT on a non-existent place returns 404 (not 500)")
        void updateNonExistentPlaceReturns404() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findById(PLACE_ID)).thenReturn(Optional.empty());
            mockMvc.perform(put("/api/v1/users/saved-places/{id}", PLACE_ID)
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdatePayload())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("DELETE on a non-existent place returns 404 (not 500)")
        void deleteNonExistentPlaceReturns404() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findById(PLACE_ID)).thenReturn(Optional.empty());
            mockMvc.perform(delete("/api/v1/users/saved-places/{id}", PLACE_ID)
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Create Saved Place")
    class CreateSavedPlace {

        @Test
        @DisplayName("Full frontend payload (userId + type) accepted -> 201 (was 400)")
        void createAcceptsFullFrontendPayload() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findByUserIdOrderByCreatedAtAsc(TEST_USER_ID)).thenReturn(java.util.List.of());
            when(savedPlaceRepository.save(any(SavedPlace.class))).thenAnswer(i -> i.getArgument(0));
            mockMvc.perform(post("/api/v1/users/saved-places")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fullFrontendPayload())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.label", is("Home")));
        }

        @Test
        @DisplayName("POST without authentication returns 401")
        void createWithoutAuthReturns401() throws Exception {
            when(savedPlaceRepository.findByUserIdOrderByCreatedAtAsc(TEST_USER_ID)).thenReturn(java.util.List.of());
            mockMvc.perform(post("/api/v1/users/saved-places")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fullFrontendPayload())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST with missing fields returns 400 (validation)")
        void createWithMissingFieldsReturns400() throws Exception {
            authenticateAs(TEST_USER_ID);
            mockMvc.perform(post("/api/v1/users/saved-places")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("userId", TEST_USER_ID.toString()))))
                    .andExpect(status().isBadRequest());
        }
    }

    private Map<String, Object> validUpdatePayload() {
        return Map.of("userId", TEST_USER_ID.toString(), "label", "Home Updated",
                "address", "9 Petal Ave", "latitude", 40.7130, "longitude", -74.0065, "type", "WORK");
    }

    private SavedPlace buildPlace(UUID id, UUID userId, String label) {
        SavedPlace p = new SavedPlace();
        p.setId(id); p.setUserId(userId); p.setLabel(label);
        p.setAddress("1 Rose Street"); p.setLatitude(40.7128); p.setLongitude(-74.006);
        return p;
    }

    @Nested
    @DisplayName("List Saved Places")
    class ListSavedPlaces {

        @Test
        @DisplayName("GET returns only the authenticated user places (IDOR-safe)")
        void listReturnsOnlyOwnPlaces() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findByUserIdOrderByCreatedAtAsc(TEST_USER_ID))
                    .thenReturn(java.util.List.of(buildPlace(PLACE_ID, TEST_USER_ID, "Home")));
            mockMvc.perform(get("/api/v1/users/saved-places")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].label", is("Home")));
        }

        @Test
        @DisplayName("GET without authentication returns 401")
        void listWithoutAuthReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/users/saved-places"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Update Saved Place")
    class UpdateSavedPlace {

        @Test
        @DisplayName("PUT updating own place returns 200")
        void updateOwnPlaceReturns200() throws Exception {
            authenticateAs(TEST_USER_ID);
            SavedPlace place = buildPlace(PLACE_ID, TEST_USER_ID, "Old");
            when(savedPlaceRepository.findById(PLACE_ID)).thenReturn(Optional.of(place));
            when(savedPlaceRepository.save(any(SavedPlace.class))).thenAnswer(i -> i.getArgument(0));
            mockMvc.perform(put("/api/v1/users/saved-places/{id}", PLACE_ID)
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdatePayload())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.label", is("Home Updated")));
        }

        @Test
        @DisplayName("PUT prevents updating another user place (IDOR protection)")
        void updateAnotherUserPlaceReturns404() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findById(PLACE_ID))
                    .thenReturn(Optional.of(buildPlace(PLACE_ID, OTHER_USER_ID, "Theirs")));
            mockMvc.perform(put("/api/v1/users/saved-places/{id}", PLACE_ID)
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdatePayload())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("not found")));
            verify(savedPlaceRepository, never()).save(any(SavedPlace.class));
        }
    }

    @Nested
    @DisplayName("Delete Saved Place")
    class DeleteSavedPlace {

        @Test
        @DisplayName("DELETE removes an existing saved place")
        void deleteSavedPlaceReturns200() throws Exception {
            authenticateAs(TEST_USER_ID);
            SavedPlace place = buildPlace(PLACE_ID, TEST_USER_ID, "To Delete");
            when(savedPlaceRepository.findById(PLACE_ID)).thenReturn(Optional.of(place));
            doNothing().when(savedPlaceRepository).delete(any(SavedPlace.class));
            mockMvc.perform(delete("/api/v1/users/saved-places/{id}", PLACE_ID)
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("DELETE prevents deleting another user place (IDOR protection)")
        void deleteAnotherUserPlaceReturns404() throws Exception {
            authenticateAs(TEST_USER_ID);
            when(savedPlaceRepository.findById(PLACE_ID))
                    .thenReturn(Optional.of(buildPlace(PLACE_ID, OTHER_USER_ID, "Theirs")));
            mockMvc.perform(delete("/api/v1/users/saved-places/{id}", PLACE_ID)
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("not found")));
            verify(savedPlaceRepository, never()).delete(any(SavedPlace.class));
        }
    }

    @Nested
    @DisplayName("Non-UUID path segment (original 500 trigger)")
    class NonUuidPathSegment {

        @Test
        @DisplayName("PUT with a non-UUID id returns 400, NOT 500")
        void putWithNonUuidIdReturns400Not500() throws Exception {
            authenticateAs(TEST_USER_ID);
            mockMvc.perform(put("/api/v1/users/saved-places/{id}", "saved-places")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdatePayload())))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message", containsString("Invalid")));
        }
    }
}
