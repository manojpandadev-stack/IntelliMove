package com.intellimove.payment.service;

import com.intellimove.common.enums.PaymentStatus;
import com.intellimove.common.enums.DomainEventType;
import com.intellimove.common.event.EventPublisher;
import com.intellimove.common.event.PaymentEvent;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.InvalidStateTransitionException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.common.outbox.OutboxService;
import com.intellimove.payment.entity.Payment;
import com.intellimove.payment.provider.PaymentProvider;
import com.intellimove.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Payment service with idempotency and state machine enforcement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final OutboxService outboxService;

    @Qualifier("sandboxPaymentProvider")
    private final PaymentProvider paymentProvider;

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
            PaymentStatus.INITIATED, Set.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED),
            PaymentStatus.PROCESSING, Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
            PaymentStatus.COMPLETED, Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED, Set.of(PaymentStatus.INITIATED),
            PaymentStatus.REFUNDED, Set.of()
    );

    @Transactional
    public Payment initiatePayment(UUID rideId, UUID customerId, BigDecimal amount,
                                    String currency, String paymentMethod) {
        // Idempotency: check if payment already exists for this ride
        Optional<Payment> existing = paymentRepository.findByRideId(rideId);
        if (existing.isPresent()) {
            log.info("Payment already exists for ride {}, returning existing", rideId);
            return existing.get();
        }

        String idempotencyKey = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .rideId(rideId)
                .customerId(customerId)
                .status(PaymentStatus.INITIATED)
                .amount(amount)
                .currency(currency != null ? currency : "USD")
                .idempotencyKey(idempotencyKey)
                .paymentMethod(paymentMethod)
                .initiatedAt(Instant.now())
                .build();

        payment = paymentRepository.save(payment);

        // Initiate with provider
        PaymentProvider.PaymentResult result = paymentProvider.initiatePayment(
                payment.getId().toString(), amount, payment.getCurrency(), paymentMethod);

        if (result.success()) {
            payment.setStatus(PaymentStatus.PROCESSING);
            payment.setProviderTransactionId(result.providerTransactionId());
            payment = paymentRepository.save(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            payment.setFailedAt(Instant.now());
            payment = paymentRepository.save(payment);
        }

        eventPublisher.publish(PaymentEvent.builder()
                .eventType(DomainEventType.PAYMENT_INITIATED.name())
                .paymentId(payment.getId().toString())
                .rideId(rideId.toString())
                .customerId(customerId.toString())
                .amount(amount)
                .currency(payment.getCurrency())
                .paymentStatus(payment.getStatus().name())
                .correlationId(rideId.toString())
                .build());

        log.info("Payment initiated: id={}, rideId={}, status={}", payment.getId(), rideId, payment.getStatus());
        return payment;
    }

    @Transactional
    public Payment confirmPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        validateTransition(payment.getStatus(), PaymentStatus.COMPLETED);

        PaymentProvider.PaymentResult result = paymentProvider.confirmPayment(payment.getId().toString());

        if (result.success()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            payment.setFailedAt(Instant.now());
        }

        payment = paymentRepository.save(payment);

        eventPublisher.publish(PaymentEvent.builder()
                .eventType(result.success()
                        ? DomainEventType.PAYMENT_COMPLETED.name()
                        : DomainEventType.PAYMENT_FAILED.name())
                .paymentId(payment.getId().toString())
                .rideId(payment.getRideId().toString())
                .customerId(payment.getCustomerId().toString())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getStatus().name())
                .failureReason(result.failureReason())
                .correlationId(payment.getRideId().toString())
                .build());

        return payment;
    }

    @Transactional
    public Payment refundPayment(UUID paymentId, BigDecimal refundAmount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        validateTransition(payment.getStatus(), PaymentStatus.REFUNDED);

        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BusinessException("REFUND_EXCEEDS_AMOUNT", "Refund exceeds payment amount");
        }

        PaymentProvider.PaymentResult result = paymentProvider.refundPayment(
                payment.getProviderTransactionId(), refundAmount);

        if (result.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundAmount(refundAmount);
            payment.setRefundedAt(Instant.now());
            payment = paymentRepository.save(payment);
        } else {
            throw new BusinessException("REFUND_FAILED", "Refund failed: " + result.failureReason());
        }

        eventPublisher.publish(PaymentEvent.builder()
                .eventType(DomainEventType.REFUND_INITIATED.name())
                .paymentId(payment.getId().toString())
                .rideId(payment.getRideId().toString())
                .customerId(payment.getCustomerId().toString())
                .amount(refundAmount)
                .currency(payment.getCurrency())
                .paymentStatus(payment.getStatus().name())
                .correlationId(payment.getRideId().toString())
                .build());

        return payment;
    }

    @Transactional
    public Payment handleWebhook(String providerTransactionId, String status, Map<String, Object> payload) {
        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> providerTransactionId.equals(p.getProviderTransactionId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment", "providerTransactionId", providerTransactionId));

        // Idempotency: skip if already completed
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Webhook ignored for completed payment: {}", payment.getId());
            return payment;
        }

        if ("COMPLETED".equals(status)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
        } else if ("FAILED".equals(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Webhook: " + status);
            payment.setFailedAt(Instant.now());
        }

        payment = paymentRepository.save(payment);

        eventPublisher.publish(PaymentEvent.builder()
                .eventType("COMPLETED".equals(status)
                        ? DomainEventType.PAYMENT_COMPLETED.name()
                        : DomainEventType.PAYMENT_FAILED.name())
                .paymentId(payment.getId().toString())
                .rideId(payment.getRideId().toString())
                .customerId(payment.getCustomerId().toString())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getStatus().name())
                .correlationId(payment.getRideId().toString())
                .build());

        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByRideId(UUID rideId) {
        return paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "rideId", rideId));
    }

    @Transactional(readOnly = true)
    public Page<Payment> getCustomerPayments(UUID customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    private void validateTransition(PaymentStatus current, PaymentStatus target) {
        Set<PaymentStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStateTransitionException("Payment", current.name(), target.name());
        }
    }
}
