package com.intellimove.driver.config;

import com.intellimove.driver.dto.DriverResponse;
import com.intellimove.driver.entity.Driver;
import com.intellimove.driver.mapper.DriverMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DriverMapperConfig {

    @Bean
    public DriverMapper driverMapper() {
        return new DriverMapper() {
            @Override
            public DriverResponse toResponse(Driver driver) {
                if (driver == null) return null;
                return DriverResponse.builder()
                        .id(driver.getId())
                        .userId(driver.getUserId())
                        .licenseNumber(driver.getLicenseNumber())
                        .status(driver.getStatus())
                        .vehicleMake(driver.getVehicleMake())
                        .vehicleModel(driver.getVehicleModel())
                        .vehicleYear(driver.getVehicleYear())
                        .vehicleColor(driver.getVehicleColor())
                        .licensePlate(driver.getLicensePlate())
                        .vehicleType(driver.getVehicleType())
                        .rating(driver.getRating())
                        .totalTrips(driver.getTotalTrips())
                        .verified(driver.isVerified())
                        .available(driver.isAvailable())
                        .createdAt(driver.getCreatedAt())
                        .build();
            }
        };
    }
}
