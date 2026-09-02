package com.intellimove.user.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.user.entity.SavedPlace;
import com.intellimove.user.repository.SavedPlaceRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Saved-place CRUD for the booking experience.
 * Served under the gateway's user-service route (/api/v1/users/**) so the
 * frontend contract `/api/v1/users/saved-places` reaches this controller.
 * (Previously mapped at /api/v1/saved-places, which no route exposed: the
 * un-routered path made every frontend call fall into UserController's
 * GET /api/v1/users/{id} template, where "saved-places" failed UUID
 * conversion -> HTTP 500. Spring MVC prefers this exact literal mapping
 * over the {id} template, so the collision is resolved.)
 *
 * Ownership is enforced with the JWT principal (SecurityUtils): a user can
 * only ever read, modify or delete their own saved places (IDOR-safe).
 */
@RestController
@RequestMapping("/api/v1/users/saved-places")
@RequiredArgsConstructor
public class SavedPlaceController {

    private final SavedPlaceRepository savedPlaceRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SavedPlace>>> list() {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                savedPlaceRepository.findByUserIdOrderByCreatedAtAsc(userId)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SavedPlace>> create(@Valid @RequestBody PlaceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }
        List<SavedPlace> existing = savedPlaceRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (existing.size() >= 10) {
            return ResponseEntity.status(422).body(ApiResponse.error(
                    "You can store up to 10 saved places. Remove one before adding another."));
        }
        if (existing.stream().anyMatch(p -> p.getLabel().equalsIgnoreCase(request.getLabel().trim()))) {
            return ResponseEntity.status(422).body(ApiResponse.error(
                    "You already have a saved place with this label."));
        }
        SavedPlace saved = savedPlaceRepository.save(SavedPlace.builder()
                .userId(userId)
                .label(request.getLabel().trim())
                .address(request.getAddress().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Saved place created", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SavedPlace>> update(@PathVariable UUID id,
                                                          @Valid @RequestBody PlaceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }
        SavedPlace place = ownedPlace(id, userId);
        place.setLabel(request.getLabel().trim());
        place.setAddress(request.getAddress().trim());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        return ResponseEntity.ok(ApiResponse.success("Saved place updated", savedPlaceRepository.save(place)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }
        SavedPlace place = ownedPlace(id, userId);
        savedPlaceRepository.delete(place);
        return ResponseEntity.ok(ApiResponse.success("Saved place deleted", null));
    }

    /** Loads the place and enforces that it belongs to the caller. */
    private SavedPlace ownedPlace(UUID id, UUID userId) {
        SavedPlace place = savedPlaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Saved place", "id", id));
        if (!place.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Saved place", "id", id);
        }
        return place;
    }

        @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaceRequest {
        @NotBlank
        @Size(max = 100)
        private String label;

        @NotBlank
        @Size(max = 255)
        private String address;

        @NotNull
        @Min(value = -90) @Max(value = 90)
        private Double latitude;

        @NotNull
        @Min(value = -180) @Max(value = 180)
        private Double longitude;
    }
}
