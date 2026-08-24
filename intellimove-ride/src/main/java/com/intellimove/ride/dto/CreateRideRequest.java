package com.intellimove.ride.dto;

import com.intellimove.common.enums.RideType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRideRequest {

    @NotNull(message = "Ride type is required")
    private RideType rideType;

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double pickupLongitude;

    private String pickupAddress;

    @NotNull(message = "Dropoff latitude is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double dropoffLatitude;

    @NotNull(message = "Dropoff longitude is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double dropoffLongitude;

    private String dropoffAddress;
}
