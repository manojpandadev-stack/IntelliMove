package com.intellimove.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Id
    /** The owning user — also the primary key (one preferences row per user). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notify_ride_updates", nullable = false)
    private boolean notifyRideUpdates = true;

    @Column(name = "notify_promotions", nullable = false)
    private boolean notifyPromotions = false;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;

    @Column(name = "notify_sms", nullable = false)
    private boolean notifySms = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onTouch() {
        updatedAt = Instant.now();
    }
}
