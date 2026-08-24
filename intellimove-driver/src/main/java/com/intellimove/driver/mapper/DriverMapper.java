package com.intellimove.driver.mapper;

import com.intellimove.driver.dto.DriverResponse;
import com.intellimove.driver.entity.Driver;
public interface DriverMapper {

    DriverResponse toResponse(Driver driver);
}
