package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideCompletedEvent extends DomainEvent {

    private String rideId;
    private String driverId;
    private String customerId;
    private BigDecimal fareAmount;
    private String currency;
    private double distanceKm;
    private long durationMinutes;
}
