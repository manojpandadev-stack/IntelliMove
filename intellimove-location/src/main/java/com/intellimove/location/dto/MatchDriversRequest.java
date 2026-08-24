package com.intellimove.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDriversRequest {

    @NotBlank
    private String rideId;

    @NotNull
    private Double pickupLatitude;

    @NotNull
    private Double pickupLongitude;

    @NotBlank
    private String rideType;
}
