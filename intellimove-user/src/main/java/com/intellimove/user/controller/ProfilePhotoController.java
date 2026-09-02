package com.intellimove.user.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.entity.UserProfilePhoto;
import com.intellimove.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Rider profile photo endpoints.
 *
 * Security:
 * - PUT/DELETE require an authenticated caller whose JWT user id matches the
 *   {userId} path variable (admins may manage any user). The identity comes
 *   from the signed token via {@link SecurityUtils} — never client headers.
 * - GET returns the stored image bytes; the frontend fetches it with the
 *   caller's Authorization header (no public unauthenticated PII endpoint).
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/photo")
@RequiredArgsConstructor
public class ProfilePhotoController {

    private final UserService userService;

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> uploadPhoto(
            @PathVariable UUID userId,
            @RequestParam("file") MultipartFile file) {
        requireOwner(userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("PHOTO_EMPTY", "Please choose an image to upload.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("PHOTO_UNREADABLE", "Could not read the selected file.");
        }
        userService.uploadProfilePhoto(userId, bytes);
        return ResponseEntity.ok(ApiResponse.success("Profile photo updated", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> removePhoto(@PathVariable UUID userId) {
        requireOwner(userId);
        userService.removeProfilePhoto(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile photo removed", null));
    }

    @GetMapping
    public ResponseEntity<byte[]> getPhoto(@PathVariable UUID userId) {
        UserProfilePhoto photo = userService.getProfilePhoto(userId)
                .orElseThrow(() -> new com.intellimove.common.exception.ResourceNotFoundException(
                        "Profile photo", "userId", userId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .contentLength(photo.getByteSize())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .body(photo.getData());
    }

    /** Only the owner (or an admin) may modify a user's profile photo. */
    private void requireOwner(UUID userId) {
        UUID current = SecurityUtils.getCurrentUserId();
        boolean admin = SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN");
        if (current == null || (!current.equals(userId) && !admin)) {
            throw new BusinessException(
                    "PHOTO_FORBIDDEN", "You can only manage your own profile photo.");
        }
    }
}
