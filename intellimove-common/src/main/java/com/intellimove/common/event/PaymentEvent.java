package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent extends DomainEvent {

    private String paymentId;
    private String rideId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentStatus;
    private String providerTransactionId;
    private String failureReason;
}
