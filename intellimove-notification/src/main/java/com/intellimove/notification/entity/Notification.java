package com.intellimove.notification.entity;

import com.intellimove.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_recipient", columnList = "recipientId"),
    @Index(name = "idx_notif_type", columnList = "notificationType"),
    @Index(name = "idx_notif_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private String recipientId;

    @Column(nullable = false)
    private String recipientType;

    @Column(nullable = false)
    private String notificationType;

    @Column(nullable = false)
    private String channel;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private boolean sent = false;

    private Instant sentAt;

    private boolean read;

    private Instant readAt;

    private String errorMessage;
}
