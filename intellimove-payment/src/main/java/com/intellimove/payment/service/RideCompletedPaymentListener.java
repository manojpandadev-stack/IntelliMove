package com.intellimove.payment.service;

import com.intellimove.common.enums.PaymentStatus;
import com.intellimove.common.event.DomainEvent;
import com.intellimove.common.event.RideCompletedEvent;
import com.intellimove.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes ride lifecycle events and automatically initiates (and confirms
 * through the sandbox provider) the payment when a trip is completed.
 *
 * The fare amount is taken from the authoritative RideCompletedEvent
 * (finalFare computed by the Ride Service) — never from client input.
 * PaymentService.initiatePayment is idempotent per ride, so redelivered
 * events cannot create duplicate charges.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RideCompletedPaymentListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = "ride-events", groupId = "payment-service")
    public void handleRideEvent(DomainEvent event) {
        if (!(event instanceof RideCompletedEvent completed)) {
            return;
        }
        if (completed.getRideId() == null || completed.getCustomerId() == null
                || completed.getFareAmount() == null) {
            log.warn("RideCompletedEvent missing required fields, skipping: correlationId={}",
                    event.getCorrelationId());
            return;
        }

        try {
            UUID rideId = UUID.fromString(completed.getRideId());
            UUID customerId = UUID.fromString(completed.getCustomerId());

            // Idempotent: returns the existing payment if one already exists for this ride.
            Payment payment = paymentService.initiatePayment(
                    rideId, customerId,
                    completed.getFareAmount(),
                    completed.getCurrency() != null ? completed.getCurrency() : "USD",
                    "WALLET");

            if (payment.getStatus() == PaymentStatus.PROCESSING) {
                paymentService.confirmPayment(payment.getId());
                log.info("Auto-payment completed for ride {}: {} {}",
                        rideId, payment.getCurrency(), completed.getFareAmount());
            } else {
                log.info("Payment for ride {} in state {} — no confirmation attempted",
                        rideId, payment.getStatus());
            }
        } catch (Exception e) {
            // Never let a payment failure crash the consumer loop;
            // the ride lifecycle event has already been processed by other consumers.
            log.error("Failed to auto-process payment for ride {}: {}",
                    completed.getRideId(), e.getMessage(), e);
        }
    }
}
