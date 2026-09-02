package com.intellimove.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Binary avatar image for a user (one row per user). Stored in its own table
 * so user listings never load binary payloads. The user's discoverable URL
 * lives in {@code users.profile_image_url} pointing at
 * {@code /api/v1/users/{id}/photo}.
 */
@Entity
@Table(name = "user_profile_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfilePhoto {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String contentType;

    /** Validated image bytes (JPEG/PNG/WebP magic bytes checked on upload). */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;
}
