package com.intellimove.ride.service;

import com.intellimove.common.enums.RideType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService();
        ReflectionTestUtils.setField(pricingService, "economyBaseFare", new BigDecimal("2.50"));
        ReflectionTestUtils.setField(pricingService, "economyPerKm", new BigDecimal("1.20"));
        ReflectionTestUtils.setField(pricingService, "economyPerMinute", new BigDecimal("0.25"));
        ReflectionTestUtils.setField(pricingService, "comfortBaseFare", new BigDecimal("4.00"));
        ReflectionTestUtils.setField(pricingService, "comfortPerKm", new BigDecimal("1.80"));
        ReflectionTestUtils.setField(pricingService, "comfortPerMinute", new BigDecimal("0.35"));
        ReflectionTestUtils.setField(pricingService, "premiumBaseFare", new BigDecimal("6.00"));
        ReflectionTestUtils.setField(pricingService, "premiumPerKm", new BigDecimal("2.50"));
        ReflectionTestUtils.setField(pricingService, "premiumPerMinute", new BigDecimal("0.50"));
        ReflectionTestUtils.setField(pricingService, "xlBaseFare", new BigDecimal("5.00"));
        ReflectionTestUtils.setField(pricingService, "xlPerKm", new BigDecimal("2.00"));
        ReflectionTestUtils.setField(pricingService, "xlPerMinute", new BigDecimal("0.40"));
        ReflectionTestUtils.setField(pricingService, "demandMultiplierDefault", BigDecimal.ONE);
    }

    @Test
    void testHaversineDistance() {
        // New York to Boston ~306 km
        double distance = pricingService.haversineDistance(40.7128, -74.006, 42.3601, -71.0589);
        assertTrue(distance > 280 && distance < 340,
                "Distance NY-Boston should be ~306 km, got: " + distance);
    }

    @Test
    void testCalculateEstimateEconomy() {
        BigDecimal fare = pricingService.calculateEstimate(40.7128, -74.006, 40.7580, -73.9855, RideType.ECONOMY);
        assertNotNull(fare);
        assertTrue(fare.compareTo(BigDecimal.ZERO) > 0, "Fare should be positive");
        assertTrue(fare.compareTo(new BigDecimal("100")) < 0, "Fare should be reasonable");
    }

    @Test
    void testCalculateEstimatePremiumMoreExpensiveThanEconomy() {
        BigDecimal economyFare = pricingService.calculateEstimate(40.7128, -74.006, 40.7580, -73.9855, RideType.ECONOMY);
        BigDecimal premiumFare = pricingService.calculateEstimate(40.7128, -74.006, 40.7580, -73.9855, RideType.PREMIUM);
        assertTrue(premiumFare.compareTo(economyFare) > 0, "Premium should be more expensive than economy");
    }

    @Test
    void testCalculateFinalFare() {
        BigDecimal fare = pricingService.calculateFinalFare(40.7128, -74.006, 40.7580, -73.9855, RideType.COMFORT, 15);
        assertNotNull(fare);
        assertTrue(fare.compareTo(BigDecimal.ZERO) > 0, "Fare should be positive");
    }

    @Test
    void testZeroDurationFare() {
        BigDecimal fare = pricingService.calculateFinalFare(40.7128, -74.006, 40.7128, -74.006, RideType.ECONOMY, 0);
        assertNotNull(fare);
        assertTrue(fare.compareTo(BigDecimal.ZERO) >= 0, "Fare should be non-negative");
    }

    @Test
    void testFareIncreasesWithDistance() {
        BigDecimal shortFare = pricingService.calculateEstimate(40.7128, -74.006, 40.7130, -74.0065, RideType.ECONOMY);
        BigDecimal longFare = pricingService.calculateEstimate(40.7128, -74.006, 40.8000, -73.9500, RideType.ECONOMY);
        assertTrue(longFare.compareTo(shortFare) > 0, "Longer trip should cost more");
    }
}
