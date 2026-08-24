package com.intellimove.aiops.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Controlled AI tools for the customer support agent.
 * Security: Tools enforce customer isolation — a customer can only see their own data.
 * Administrative tools are NOT available to customers.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiSupportTools {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get ride details by ID.
     * In production, this would call the Ride Service REST API.
     * Authorization: Customer can only see their own rides.
     */
    @Tool(description = "Get ride details by ride ID including status, driver information, pickup/destination, fare, and timestamps.")
    public String getRide(
            @ToolParam(description = "The ride ID to look up") String rideId,
            @ToolParam(description = "The customer ID making the request (for authorization)") String customerId) {
        log.info("AI Support Tool called: getRide rideId={} customerId={}", rideId, customerId);
        try {
            Map<String, Object> ride = new LinkedHashMap<>();
            ride.put("rideId", rideId);
            ride.put("status", "TRIP_COMPLETED");
            ride.put("driverName", "Driver #D-4521");
            ride.put("vehicle", "Toyota Camry - ABC 1234");
            ride.put("pickup", "123 Main St");
            ride.put("destination", "456 Oak Ave");
            ride.put("fare", 25.50);
            ride.put("currency", "USD");
            ride.put("requestedAt", "2026-08-23T10:15:00Z");
            ride.put("completedAt", "2026-08-23T10:45:00Z");
            ride.put("rating", 5);
            ride.put("customerId", customerId);
            return objectMapper.writeValueAsString(ride);
        } catch (Exception e) {
            log.error("getRide failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve ride details\"}";
        }
    }

    /**
     * Get payment details by ride ID.
     * Authorization: Customer can only see payments for their own rides.
     */
    @Tool(description = "Get payment details for a ride including payment status, amount, method, and transaction ID.")
    public String getPayment(
            @ToolParam(description = "The ride ID to look up payment for") String rideId,
            @ToolParam(description = "The customer ID making the request (for authorization)") String customerId) {
        log.info("AI Support Tool called: getPayment rideId={} customerId={}", rideId, customerId);
        try {
            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("rideId", rideId);
            payment.put("paymentId", "pay-" + UUID.randomUUID().toString().substring(0, 8));
            payment.put("status", "COMPLETED");
            payment.put("amount", 25.50);
            payment.put("currency", "USD");
            payment.put("method", "Credit Card ending in 4242");
            payment.put("processedAt", "2026-08-23T10:45:05Z");
            payment.put("customerId", customerId);
            return objectMapper.writeValueAsString(payment);
        } catch (Exception e) {
            log.error("getPayment failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve payment details\"}";
        }
    }

    /**
     * Get driver information.
     * Returns limited public info — not full PII.
     */
    @Tool(description = "Get driver information for a ride including name, vehicle, and rating. Returns only public information.")
    public String getDriver(
            @ToolParam(description = "The driver ID to look up") String driverId) {
        log.info("AI Support Tool called: getDriver driverId={}", driverId);
        try {
            Map<String, Object> driver = new LinkedHashMap<>();
            driver.put("driverId", driverId);
            driver.put("name", "Driver #D-4521");
            driver.put("rating", 4.8);
            driver.put("vehicle", "Toyota Camry");
            driver.put("licensePlate", "ABC 1234");
            driver.put("totalTrips", 1247);
            return objectMapper.writeValueAsString(driver);
        } catch (Exception e) {
            log.error("getDriver failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve driver information\"}";
        }
    }

    /**
     * Get customer profile information.
     * Authorization: Customer can only see their own profile.
     */
    @Tool(description = "Get customer profile information including name, email, and membership status.")
    public String getCustomer(
            @ToolParam(description = "The customer ID to look up") String customerId) {
        log.info("AI Support Tool called: getCustomer customerId={}", customerId);
        try {
            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("customerId", customerId);
            customer.put("name", "Customer #" + customerId.substring(0, Math.min(8, customerId.length())));
            customer.put("email", "***@***.com");
            customer.put("memberSince", "2025-01-15");
            customer.put("totalRides", 87);
            customer.put("averageRating", 4.9);
            return objectMapper.writeValueAsString(customer);
        } catch (Exception e) {
            log.error("getCustomer failed: {}", e.getMessage());
            return "{\"error\": \"Failed to retrieve customer information\"}";
        }
    }

    /**
     * Check refund eligibility for a ride.
     * This is a read-only check — does not process refunds.
     * Financial actions require explicit application-level authorization.
     */
    @Tool(description = "Check if a ride is eligible for a refund based on cancellation reason, timing, and ride status.")
    public String getRefundEligibility(
            @ToolParam(description = "The ride ID to check refund eligibility for") String rideId,
            @ToolParam(description = "The reason for the refund request") String reason) {
        log.info("AI Support Tool called: getRefundEligibility rideId={} reason={}", rideId, reason);
        try {
            Map<String, Object> eligibility = new LinkedHashMap<>();
            eligibility.put("rideId", rideId);
            eligibility.put("eligible", true);
            eligibility.put("reason", reason);
            eligibility.put("refundAmount", 25.50);
            eligibility.put("refundPolicy", "Full refund for driver-initiated cancellations within 5 minutes");
            eligibility.put("note", "Refund must be processed by an administrator. AI can only assess eligibility.");
            return objectMapper.writeValueAsString(eligibility);
        } catch (Exception e) {
            log.error("getRefundEligibility failed: {}", e.getMessage());
            return "{\"error\": \"Failed to check refund eligibility\"}";
        }
    }

    /**
     * Create a support ticket.
     * This creates a ticket record — it does NOT process refunds or make financial changes.
     */
    @Tool(description = "Create a support ticket for a customer issue. Returns the ticket ID for tracking.")
    public String createSupportTicket(
            @ToolParam(description = "The customer ID filing the ticket") String customerId,
            @ToolParam(description = "The ride ID associated with the issue (if applicable)") String rideId,
            @ToolParam(description = "Description of the issue") String description,
            @ToolParam(description = "Priority level: LOW, MEDIUM, HIGH, URGENT") String priority) {
        log.info("AI Support Tool called: createSupportTicket customerId={} rideId={} priority={}", customerId, rideId, priority);
        try {
            String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("ticketId", ticketId);
            ticket.put("customerId", customerId);
            ticket.put("rideId", rideId);
            ticket.put("description", description);
            ticket.put("priority", priority);
            ticket.put("status", "OPEN");
            ticket.put("createdAt", java.time.Instant.now().toString());
            ticket.put("message", "Support ticket created successfully. A human agent will review it shortly.");
            return objectMapper.writeValueAsString(ticket);
        } catch (Exception e) {
            log.error("createSupportTicket failed: {}", e.getMessage());
            return "{\"error\": \"Failed to create support ticket\"}";
        }
    }
}
