package com.intellimove.location.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {

    private String driverId;
    private double score;
    private double distanceKm;
}
