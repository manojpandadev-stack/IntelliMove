package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Published when an assigned driver rejects an incoming ride request while the
 * ride is DRIVER_ASSIGNED. The ride goes back to REQUESTED (no cancellation),
 * letting the matching system select another eligible driver.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverRejectedEvent extends DomainEvent {

    private String rideId;
    private String driverId;
    private String customerId;
    private double pickupLatitude;
    private double pickupLongitude;
    private String rideType;
}