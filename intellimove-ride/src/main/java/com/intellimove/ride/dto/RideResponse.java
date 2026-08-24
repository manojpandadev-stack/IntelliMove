package com.intellimove.ride.dto;

import com.intellimove.common.enums.CancellationReason;
import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideResponse {

    private UUID id;
    private UUID customerId;
    private UUID driverId;
    private RideStatus status;
    private RideType rideType;
    private double pickupLatitude;
    private double pickupLongitude;
    private String pickupAddress;
    private double dropoffLatitude;
    private double dropoffLongitude;
    private String dropoffAddress;
    private BigDecimal estimatedFare;
    private BigDecimal finalFare;
    private String currency;
    private double distanceKm;
    private long durationMinutes;
    private Instant driverAssignedAt;
    private Instant driverAcceptedAt;
    private Instant tripStartedAt;
    private Instant tripCompletedAt;
    private Instant cancelledAt;
    private CancellationReason cancellationReason;
    private String cancelledBy;
    private Instant createdAt;
}
