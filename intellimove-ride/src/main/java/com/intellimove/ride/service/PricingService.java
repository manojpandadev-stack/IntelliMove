package com.intellimove.ride.service;

import com.intellimove.common.enums.RideType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Configurable pricing engine.
 * Fare = (baseFare + distanceFare + timeFare) * demandMultiplier
 */
@Service
@Slf4j
public class PricingService {

    @Value("${pricing.economy.base-fare:2.50}")
    private BigDecimal economyBaseFare;

    @Value("${pricing.economy.per-km:1.20}")
    private BigDecimal economyPerKm;

    @Value("${pricing.economy.per-minute:0.25}")
    private BigDecimal economyPerMinute;

    @Value("${pricing.comfort.base-fare:4.00}")
    private BigDecimal comfortBaseFare;

    @Value("${pricing.comfort.per-km:1.80}")
    private BigDecimal comfortPerKm;

    @Value("${pricing.comfort.per-minute:0.35}")
    private BigDecimal comfortPerMinute;

    @Value("${pricing.premium.base-fare:6.00}")
    private BigDecimal premiumBaseFare;

    @Value("${pricing.premium.per-km:2.50}")
    private BigDecimal premiumPerKm;

    @Value("${pricing.premium.per-minute:0.50}")
    private BigDecimal premiumPerMinute;

    @Value("${pricing.xl.base-fare:5.00}")
    private BigDecimal xlBaseFare;

    @Value("${pricing.xl.per-km:2.00}")
    private BigDecimal xlPerKm;

    @Value("${pricing.xl.per-minute:0.40}")
    private BigDecimal xlPerMinute;

    @Value("${pricing.demand-multiplier-default:1.0}")
    private BigDecimal demandMultiplierDefault;

    public BigDecimal calculateEstimate(double pickupLat, double pickupLng,
                                        double dropoffLat, double dropoffLng,
                                        RideType rideType) {
        double distanceKm = haversineDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
        long estimatedMinutes = (long) (distanceKm / 0.5); // rough: 30 km/h avg
        return calculateFare(distanceKm, estimatedMinutes, rideType, demandMultiplierDefault);
    }

    public BigDecimal calculateFinalFare(double pickupLat, double pickupLng,
                                          double dropoffLat, double dropoffLng,
                                          RideType rideType, long durationMinutes) {
        double distanceKm = haversineDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
        return calculateFare(distanceKm, durationMinutes, rideType, demandMultiplierDefault);
    }

    private BigDecimal calculateFare(double distanceKm, long durationMinutes,
                                      RideType rideType, BigDecimal demandMultiplier) {
        BigDecimal[] rates = getRatesForType(rideType);
        BigDecimal baseFare = rates[0];
        BigDecimal perKm = rates[1];
        BigDecimal perMinute = rates[2];

        BigDecimal distanceFare = perKm.multiply(BigDecimal.valueOf(distanceKm));
        BigDecimal timeFare = perMinute.multiply(BigDecimal.valueOf(durationMinutes));
        BigDecimal subtotal = baseFare.add(distanceFare).add(timeFare);
        BigDecimal total = subtotal.multiply(demandMultiplier).setScale(2, RoundingMode.HALF_UP);

        log.debug("Pricing: base={}, distance={}, time={}, multiplier={}, total={}",
                baseFare, distanceFare, timeFare, demandMultiplier, total);
        return total;
    }

    private BigDecimal[] getRatesForType(RideType rideType) {
        return switch (rideType) {
            case ECONOMY -> new BigDecimal[]{economyBaseFare, economyPerKm, economyPerMinute};
            case COMFORT -> new BigDecimal[]{comfortBaseFare, comfortPerKm, comfortPerMinute};
            case PREMIUM -> new BigDecimal[]{premiumBaseFare, premiumPerKm, premiumPerMinute};
            case XL -> new BigDecimal[]{xlBaseFare, xlPerKm, xlPerMinute};
            case DELIVERY -> new BigDecimal[]{economyBaseFare, economyPerKm, economyPerMinute};
        };
    }

    /**
     * The demand multiplier currently applied to every fare calculation.
     * Exposed so fare previews can display the actual surge factor that was
     * used, instead of duplicating pricing logic in the presentation layer.
     */
    public BigDecimal getDemandMultiplier() {
        return demandMultiplierDefault;
    }

    /**
     * Haversine formula for distance between two coordinates.
     */
    public double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
