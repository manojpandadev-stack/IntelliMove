package com.intellimove.location.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindDriversRequest {

    private double latitude;
    private double longitude;
    private double radiusKm;
}
