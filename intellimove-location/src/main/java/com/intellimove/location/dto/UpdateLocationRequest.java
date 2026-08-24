package com.intellimove.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

/**
 * Request body for driver location updates.
 * Uses typed validation instead of raw Map to prevent HTTP 500 on invalid input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", inclusive = true, message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", inclusive = true, message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", inclusive = true, message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", inclusive = true, message = "Longitude must be between -180 and 180")
    private Double longitude;

    /**
     * Optional ride ID to associate this location update with an active ride.
     * When present, the driver's location is broadcast to ride subscribers via WebSocket.
     */
    private String rideId;

    private Map<String, String> metadata;
}
