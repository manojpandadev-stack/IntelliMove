package com.intellimove.ride.config;

import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.entity.Ride;
import com.intellimove.ride.mapper.RideMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual RideMapper registration.
 * Bypasses MapStruct @Generated class scanning issue in Spring Boot 3.x.
 */
@Configuration
public class RideMapperConfig {

    @Bean
    public RideMapper rideMapper() {
        return new RideMapper() {
            @Override
            public RideResponse toResponse(Ride ride) {
                if (ride == null) return null;
                return RideResponse.builder()
                        .id(ride.getId())
                        .customerId(ride.getCustomerId())
                        .driverId(ride.getDriverId())
                        .status(ride.getStatus())
                        .rideType(ride.getRideType())
                        .pickupLatitude(ride.getPickupLatitude())
                        .pickupLongitude(ride.getPickupLongitude())
                        .pickupAddress(ride.getPickupAddress())
                        .dropoffLatitude(ride.getDropoffLatitude())
                        .dropoffLongitude(ride.getDropoffLongitude())
                        .dropoffAddress(ride.getDropoffAddress())
                        .estimatedFare(ride.getEstimatedFare())
                        .finalFare(ride.getFinalFare())
                        .currency(ride.getCurrency())
                        .distanceKm(ride.getDistanceKm())
                        .durationMinutes(ride.getDurationMinutes())
                        .driverAssignedAt(ride.getDriverAssignedAt())
                        .driverAcceptedAt(ride.getDriverAcceptedAt())
                        .tripStartedAt(ride.getTripStartedAt())
                        .tripCompletedAt(ride.getTripCompletedAt())
                        .cancelledAt(ride.getCancelledAt())
                        .cancellationReason(ride.getCancellationReason())
                        .cancelledBy(ride.getCancelledBy())
                        .createdAt(ride.getCreatedAt())
                        .build();
            }
        };
    }
}
