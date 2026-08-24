package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverAssignedEvent extends DomainEvent {

    private String rideId;
    private String driverId;
    private String customerId;
    private double driverLatitude;
    private double driverLongitude;
    private double estimatedArrivalMinutes;
}
