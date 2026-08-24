package com.intellimove.driver.dto;

import com.intellimove.common.enums.RideType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDriverRequest {

    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotBlank(message = "Vehicle make is required")
    private String vehicleMake;

    @NotBlank(message = "Vehicle model is required")
    private String vehicleModel;

    @NotNull(message = "Vehicle year is required")
    @Min(value = 2000, message = "Vehicle must be year 2000 or newer")
    private Integer vehicleYear;

    @NotBlank(message = "Vehicle color is required")
    private String vehicleColor;

    @NotBlank(message = "License plate is required")
    private String licensePlate;

    @NotNull(message = "Vehicle type is required")
    private RideType vehicleType;
}
