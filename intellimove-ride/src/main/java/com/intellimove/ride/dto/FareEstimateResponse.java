package com.intellimove.ride.dto;

import com.intellimove.common.enums.RideType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fare preview returned before a ride is created.
 * Values are produced by the same PricingService used at ride creation time
 * so the estimate is consistent with the estimated fare stored on the ride.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareEstimateResponse {

    private double distanceKm;

    private long estimatedMinutes;

    private String currency;

    private List<RideOptionEstimate> options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RideOptionEstimate {
        private RideType rideType;
        private BigDecimal estimatedFare;
        private long etaMinutes;
        private int capacity;

        /** Deterministic human-readable category description for booking UIs. */
        private String description;

        /**
         * Demand multiplier actually applied to this option's fare by the
         * pricing engine (1.0 = no surge). Additive metadata for the booking UI.
         */
        private BigDecimal surgeMultiplier;
    }
}
