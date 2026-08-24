package com.intellimove.ride.mapper;

import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.entity.Ride;

/**
 * Mapper interface for Ride entity to RideResponse DTO.
 * Bean registration handled by RideMapperConfig to avoid
 * MapStruct @Generated class scanning issue in Spring Boot 3.x.
 */
public interface RideMapper {

    RideResponse toResponse(Ride ride);
}
