package com.intellimove.ride.entity;

import com.intellimove.common.enums.CancellationReason;
import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
import com.intellimove.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides", indexes = {
    @Index(name = "idx_ride_customer", columnList = "customerId"),
    @Index(name = "idx_ride_driver", columnList = "driverId"),
    @Index(name = "idx_ride_status", columnList = "status"),
    @Index(name = "idx_ride_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride extends BaseEntity {

    @Column(nullable = false)
    private UUID customerId;

    /**
     * Authenticated driver user ID (JWT principal / auth user.id).
     * This is NOT Driver.id (driver profile). Location GEO and matching use the same identity.
     */
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideType rideType;

    // Pickup
    @Column(nullable = false)
    private double pickupLatitude;

    @Column(nullable = false)
    private double pickupLongitude;

    private String pickupAddress;

    // Dropoff
    @Column(nullable = false)
    private double dropoffLatitude;

    @Column(nullable = false)
    private double dropoffLongitude;

    private String dropoffAddress;

    // Fare
    private BigDecimal estimatedFare;
    private BigDecimal finalFare;
    private String currency;

    // Trip data
    private double distanceKm;
    private long durationMinutes;

    // Timestamps for ride phases
    private Instant driverAssignedAt;
    private Instant driverAcceptedAt;
    private Instant driverArrivingAt;
    private Instant tripStartedAt;
    private Instant tripCompletedAt;
    private Instant cancelledAt;

    // Cancellation
    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;
    private String cancellationNote;
    private String cancelledBy;

    // Payment
    private UUID paymentId;
}
