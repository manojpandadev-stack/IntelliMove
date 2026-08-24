package com.intellimove.payment.service;

import com.intellimove.common.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
            PaymentStatus.INITIATED, Set.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED),
            PaymentStatus.PROCESSING, Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
            PaymentStatus.COMPLETED, Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED, Set.of(PaymentStatus.INITIATED),
            PaymentStatus.REFUNDED, Set.of()
    );

    private boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    @Test
    void testHappyPath() {
        assertTrue(canTransition(PaymentStatus.INITIATED, PaymentStatus.PROCESSING));
        assertTrue(canTransition(PaymentStatus.PROCESSING, PaymentStatus.COMPLETED));
    }

    @Test
    void testRefund() {
        assertTrue(canTransition(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED));
    }

    @Test
    void testRetryAfterFailure() {
        assertTrue(canTransition(PaymentStatus.FAILED, PaymentStatus.INITIATED));
    }

    @Test
    void testFailedFromInitiated() {
        assertTrue(canTransition(PaymentStatus.INITIATED, PaymentStatus.FAILED));
    }

    // Invalid
    @Test
    void testRefundedIsTerminal() {
        assertTrue(VALID_TRANSITIONS.get(PaymentStatus.REFUNDED).isEmpty());
    }

    @Test
    void testCompletedFromInitiated_Invalid() {
        assertFalse(canTransition(PaymentStatus.INITIATED, PaymentStatus.COMPLETED));
    }

    @Test
    void testRefundedFromProcessing_Invalid() {
        assertFalse(canTransition(PaymentStatus.PROCESSING, PaymentStatus.REFUNDED));
    }

    @Test
    void testCompletedFromFailed_Invalid() {
        assertFalse(canTransition(PaymentStatus.FAILED, PaymentStatus.COMPLETED));
    }
}
