package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestedEvent extends DomainEvent {

    private String rideId;
    private String customerId;
    private String driverId;
    private double pickupLatitude;
    private double pickupLongitude;
    private double dropoffLatitude;
    private double dropoffLongitude;
    private String rideType;
    private String pickupAddress;
    private String dropoffAddress;
}
