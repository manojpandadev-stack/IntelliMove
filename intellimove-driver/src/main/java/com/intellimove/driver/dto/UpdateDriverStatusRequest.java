package com.intellimove.driver.dto;

import com.intellimove.common.enums.DriverStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDriverStatusRequest {

    @NotNull(message = "Status is required")
    private DriverStatus status;
}
