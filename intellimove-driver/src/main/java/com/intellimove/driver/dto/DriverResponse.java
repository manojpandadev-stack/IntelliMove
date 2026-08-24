package com.intellimove.driver.dto;

import com.intellimove.common.enums.DriverStatus;
import com.intellimove.common.enums.RideType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {

    private UUID id;
    private UUID userId;
    private String licenseNumber;
    private DriverStatus status;
    private String vehicleMake;
    private String vehicleModel;
    private int vehicleYear;
    private String vehicleColor;
    private String licensePlate;
    private RideType vehicleType;
    private double rating;
    private int totalTrips;
    private boolean verified;
    private boolean available;
    private Instant createdAt;
}
