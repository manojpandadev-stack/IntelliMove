package com.intellimove.payment.dto;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRequest {

    private String providerTransactionId;
    private String status;
    private Map<String, Object> payload;
}
