package com.intellimove.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRequest {

    @NotNull
    private UUID rideId;

    @NotNull
    private UUID customerId;

    @NotNull
    private BigDecimal amount;

    private String currency;

    private String paymentMethod;
}
