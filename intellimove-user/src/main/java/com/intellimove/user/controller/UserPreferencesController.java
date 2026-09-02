package com.intellimove.user.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.user.entity.UserPreferences;
import com.intellimove.user.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Per-user notification preferences. Server-side persistence scoped to the
 * JWT principal — a user can only ever read/update their own row (IDOR-safe).
 */
@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesRepository preferencesRepository;

    /** Returns the caller's preferences, creating sensible defaults on first access. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<UserPreferences>> get() {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }
        UserPreferences prefs = preferencesRepository.findById(userId)
                .orElseGet(() -> preferencesRepository.save(UserPreferences.builder().userId(userId).build()));
        return ResponseEntity.ok(ApiResponse.success(prefs));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<UserPreferences>> update(@RequestBody PreferencesRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }
        UserPreferences prefs = preferencesRepository.findById(userId)
                .orElseGet(() -> UserPreferences.builder().userId(userId).build());
        prefs.setNotifyRideUpdates(request.isNotifyRideUpdates());
        prefs.setNotifyPromotions(request.isNotifyPromotions());
        prefs.setNotifyEmail(request.isNotifyEmail());
        prefs.setNotifySms(request.isNotifySms());
        UserPreferences saved = preferencesRepository.save(prefs);
        return ResponseEntity.ok(ApiResponse.success("Preferences saved", saved));
    }

    @lombok.Data
    public static class PreferencesRequest {
        private boolean notifyRideUpdates;
        private boolean notifyPromotions;
        private boolean notifyEmail;
        private boolean notifySms;
    }
}
