package com.intellimove.payment.entity;

import com.intellimove.common.enums.PaymentStatus;
import com.intellimove.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_ride", columnList = "rideId"),
    @Index(name = "idx_payment_customer", columnList = "customerId"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_idempotency", columnList = "idempotencyKey", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @Column(nullable = false)
    private UUID rideId;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String providerTransactionId;

    private String paymentMethod;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private String failureReason;

    private Instant initiatedAt;
    private Instant completedAt;
    private Instant failedAt;
    private Instant refundedAt;

    private BigDecimal refundAmount;
}
