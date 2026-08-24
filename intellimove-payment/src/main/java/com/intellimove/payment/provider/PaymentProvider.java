package com.intellimove.payment.provider;

import java.math.BigDecimal;

/**
 * Payment provider abstraction. Never couple the domain to a specific provider.
 * Implementations: SandboxPaymentProvider, StripePaymentProvider, etc.
 */
public interface PaymentProvider {

    PaymentResult initiatePayment(String transactionId, BigDecimal amount,
                                   String currency, String paymentMethod);

    PaymentResult confirmPayment(String transactionId);

    PaymentResult refundPayment(String transactionId, BigDecimal amount);

    PaymentResult getStatus(String transactionId);

    record PaymentResult(boolean success, String providerTransactionId,
                          String status, String failureReason) {}
}
