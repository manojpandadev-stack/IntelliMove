package com.intellimove.payment.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sandbox payment provider for testing. Simulates payment processing.
 * In production, replace with real provider (Stripe, Adyen, etc.)
 */
@Component("sandboxPaymentProvider")
@Slf4j
public class SandboxPaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult initiatePayment(String transactionId, BigDecimal amount,
                                          String currency, String paymentMethod) {
        log.info("Sandbox: Initiating payment {} for {} {}", transactionId, amount, currency);
        String providerTxnId = "sandbox_" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentResult(true, providerTxnId, "PROCESSING", null);
    }

    @Override
    public PaymentResult confirmPayment(String transactionId) {
        log.info("Sandbox: Confirming payment {}", transactionId);
        // Simulate 95% success rate
        boolean success = Math.random() > 0.05;
        if (success) {
            return new PaymentResult(true, transactionId, "COMPLETED", null);
        } else {
            return new PaymentResult(false, transactionId, "FAILED", "Simulated payment failure");
        }
    }

    @Override
    public PaymentResult refundPayment(String transactionId, BigDecimal amount) {
        log.info("Sandbox: Refunding {} for transaction {}", amount, transactionId);
        return new PaymentResult(true, transactionId, "REFUNDED", null);
    }

    @Override
    public PaymentResult getStatus(String transactionId) {
        log.info("Sandbox: Checking status for {}", transactionId);
        return new PaymentResult(true, transactionId, "COMPLETED", null);
    }
}
