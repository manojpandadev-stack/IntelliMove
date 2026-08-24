package com.intellimove.driver.entity;

import com.intellimove.common.enums.DriverStatus;
import com.intellimove.common.enums.RideType;
import com.intellimove.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drivers", indexes = {
    @Index(name = "idx_driver_user_id", columnList = "userId"),
    @Index(name = "idx_driver_status", columnList = "status"),
    @Index(name = "idx_driver_license", columnList = "licenseNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DriverStatus status = DriverStatus.OFFLINE;

    // Vehicle details
    @Column(nullable = false)
    private String vehicleMake;

    @Column(nullable = false)
    private String vehicleModel;

    @Column(nullable = false)
    private int vehicleYear;

    @Column(nullable = false)
    private String vehicleColor;

    @Column(nullable = false)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RideType vehicleType = RideType.ECONOMY;

    // Rating
    @Column(nullable = false)
    @Builder.Default
    private double rating = 5.0;

    @Column(nullable = false)
    @Builder.Default
    private int totalRatings = 0;

    @Column(nullable = false)
    @Builder.Default
    private int totalTrips = 0;

    // Verification
    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    private Instant verifiedAt;

    // Availability
    @Column(nullable = false)
    @Builder.Default
    private boolean available = false;

    private Instant lastLocationUpdateAt;
}
