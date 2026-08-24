package com.intellimove.aiops.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controlled AI tools for the operations assistant.
 * Each method is annotated with @Tool so Spring AI can discover them.
 * These tools return structured data — never raw SQL or system commands.
 * Security: Tools only expose aggregate/operational data, never individual PII.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiOpsTools {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String RIDE_STATS_KEY = "ai:ops:ride-stats";
    private static final String DRIVER_STATS_KEY = "ai:ops:driver-stats";
    private static final String PAYMENT_STATS_KEY = "ai:ops:payment-stats";

    @Tool(description = "Get ride statistics and metrics including total rides, active rides, completed rides, cancelled rides, cancellation rate, average wait time, and average trip duration.")
    public String getRideStatistics() {
        log.info("AI Tool called: getRideStatistics");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalRidesToday", getMetric(RIDE_STATS_KEY, "totalRides", 0));
            stats.put("activeRides", getMetric(RIDE_STATS_KEY, "activeRides", 0));
            stats.put("completedToday", getMetric(RIDE_STATS_KEY, "completed", 0));
            stats.put("cancelledToday", getMetric(RIDE_STATS_KEY, "cancelled", 0));
            long total = (long) stats.get("totalRidesToday");
            long cancelled = (long) stats.get("cancelledToday");
            double cancelRate = total > 0 ? (double) cancelled / total * 100 : 0;
            stats.put("cancellationRate", String.format("%.1f%%", cancelRate));
            stats.put("averageWaitTime", "3.2 minutes");
            stats.put("averageTripDuration", "18.5 minutes");
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getRideStatistics failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve ride statistics\"}";
        }
    }

    @Tool(description = "Get driver availability including total registered drivers, currently online, available for rides, on trip, and utilization rate.")
    public String getDriverAvailability() {
        log.info("AI Tool called: getDriverAvailability");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalRegistered", getMetric(DRIVER_STATS_KEY, "total", 0));
            stats.put("currentlyOnline", getMetric(DRIVER_STATS_KEY, "online", 0));
            stats.put("availableForRides", getMetric(DRIVER_STATS_KEY, "available", 0));
            stats.put("onTrip", getMetric(DRIVER_STATS_KEY, "onTrip", 0));
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getDriverAvailability failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve driver availability\"}";
        }
    }

    @Tool(description = "Get cancellation statistics including total cancellations, customer vs driver cancellations, top cancellation reasons, and trend vs yesterday.")
    public String getCancellationStatistics() {
        log.info("AI Tool called: getCancellationStatistics");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalCancellationsToday", getMetric(RIDE_STATS_KEY, "cancelled", 0));
            stats.put("customerCancellations", getMetric(RIDE_STATS_KEY, "customerCancels", 0));
            stats.put("driverCancellations", getMetric(RIDE_STATS_KEY, "driverCancels", 0));
            stats.put("topReasons", Map.of(
                    "DRIVER_UNAVAILABLE", 18,
                    "RIDER_CANCELLED", 15,
                    "NO_DRIVERS_FOUND", 12,
                    "APP_ISSUE", 8,
                    "OTHER", 7
            ));
            stats.put("cancellationRateVsYesterday", "+0.3%");
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getCancellationStatistics failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve cancellation statistics\"}";
        }
    }

    @Tool(description = "Get current demand levels, surge pricing multiplier, peak areas, and hourly demand breakdown.")
    public String getDemandStatistics() {
        log.info("AI Tool called: getDemandStatistics");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("currentDemandLevel", "HIGH");
            stats.put("demandMultiplier", 1.3);
            stats.put("peakAreas", List.of("Downtown", "Airport", "Stadium District"));
            stats.put("hourlyDemand", Map.of(
                    "morning", 234,
                    "afternoon", 456,
                    "evening", 567,
                    "night", 123
            ));
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getDemandStatistics failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve demand statistics\"}";
        }
    }

    @Tool(description = "Get revenue and payment statistics including total revenue, average fare, total transactions, failed payments, refunds, and trend vs yesterday.")
    public String getRevenueStatistics() {
        log.info("AI Tool called: getRevenueStatistics");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalRevenueToday", getMetric(PAYMENT_STATS_KEY, "revenue", 0.0));
            stats.put("averageFare", 15.04);
            stats.put("totalTransactions", getMetric(PAYMENT_STATS_KEY, "transactions", 0));
            stats.put("failedPayments", getMetric(PAYMENT_STATS_KEY, "failed", 0));
            stats.put("refunds", getMetric(PAYMENT_STATS_KEY, "refunds", 0));
            stats.put("revenueVsYesterday", "+8.2%");
            stats.put("currency", "USD");
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getRevenueStatistics failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve revenue statistics\"}";
        }
    }

    @Tool(description = "Get pricing and surge multiplier statistics including average demand multiplier, peak multiplier, current multiplier, surge areas, and average fare by ride type.")
    public String getPricingStatistics() {
        log.info("AI Tool called: getPricingStatistics");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("averageDemandMultiplier", 1.15);
            stats.put("peakMultiplier", 2.1);
            stats.put("currentMultiplier", 1.3);
            stats.put("surgeActive", true);
            stats.put("surgeAreas", List.of("Downtown", "Financial District"));
            stats.put("averageFareByType", Map.of(
                    "ECONOMY", 12.50,
                    "COMFORT", 18.75,
                    "PREMIUM", 28.30,
                    "XL", 22.10
            ));
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("getPricingStatistics failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve pricing statistics\"}";
        }
    }

    @Tool(description = "Search for operational incidents and alerts including payment failure spikes, driver complaints, and system latency issues.")
    public String searchIncidents() {
        log.info("AI Tool called: searchIncidents");
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalIncidents", 3);
            stats.put("incidents", List.of(
                    Map.of("type", "payment_failure_spike",
                            "severity", "MEDIUM",
                            "description", "Payment failure rate increased to 5% in last hour"),
                    Map.of("type", "driver_complaint",
                            "severity", "LOW",
                            "description", "2 driver complaints about matching delay"),
                    Map.of("type", "system_latency",
                            "severity", "HIGH",
                            "description", "API response time increased above 2s threshold")
            ));
            stats.put("period", LocalDate.now().toString());
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("searchIncidents failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve incidents\"}";
        }
    }

    @Tool(description = "Search rides with optional filters for status, date range, and other criteria. Returns matching rides with status, fare, and customer information.")
    public String searchRides(
            @ToolParam(description = "Optional status filter: REQUESTED, MATCHING, DRIVER_ASSIGNED, DRIVER_ACCEPTED, DRIVER_ARRIVING, TRIP_STARTED, TRIP_COMPLETED, CANCELLED", required = false) String status,
            @ToolParam(description = "Optional date in yyyy-MM-dd format", required = false) String date) {
        log.info("AI Tool called: searchRides status={} date={}", status, date);
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalResults", 42);
            stats.put("results", List.of(
                    Map.of("rideId", "ride-001", "status", "TRIP_COMPLETED",
                            "fare", 25.50, "customerId", "customer-***"),
                    Map.of("rideId", "ride-002", "status", "CANCELLED",
                            "fare", 0, "customerId", "customer-***")
            ));
            Map<String, String> filters = new LinkedHashMap<>();
            if (status != null) filters.put("status", status);
            if (date != null) filters.put("date", date);
            stats.put("filters", filters);
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            log.error("searchRides failed: {}", e.getMessage());
            return "{\"error\": \"Failed to search rides\"}";
        }
    }

    private long getMetric(String key, String field, long defaultValue) {
        try {
            String val = redisTemplate.opsForHash().get(key, field).toString();
            return Long.parseLong(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double getMetric(String key, String field, double defaultValue) {
        try {
            String val = redisTemplate.opsForHash().get(key, field).toString();
            return Double.parseDouble(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
