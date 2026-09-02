package com.intellimove.payment.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.dto.PagedResponse;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.payment.dto.InitiatePaymentRequest;
import com.intellimove.payment.dto.WebhookRequest;
import com.intellimove.payment.entity.Payment;
import com.intellimove.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SYSTEM')")
    public ResponseEntity<ApiResponse<Payment>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        Payment payment = paymentService.initiatePayment(
                request.getRideId(), request.getCustomerId(),
                request.getAmount(), request.getCurrency(), request.getPaymentMethod());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated", payment));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SYSTEM')")
    public ResponseEntity<ApiResponse<Payment>> confirmPayment(@PathVariable UUID id) {
        Payment payment = paymentService.confirmPayment(id);
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed", payment));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Payment>> refundPayment(
            @PathVariable UUID id,
            @RequestParam java.math.BigDecimal amount) {
        Payment payment = paymentService.refundPayment(id, amount);
        return ResponseEntity.ok(ApiResponse.success("Refund processed", payment));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Payment>> handleWebhook(
            @RequestBody WebhookRequest request) {
        Payment payment = paymentService.handleWebhook(
                request.getProviderTransactionId(), request.getStatus(), request.getPayload());
        return ResponseEntity.ok(ApiResponse.success("Webhook processed", payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Payment>> getPayment(@PathVariable UUID id) {
        Payment payment = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @GetMapping("/ride/{rideId}")
    public ResponseEntity<ApiResponse<Payment>> getPaymentByRideId(@PathVariable UUID rideId) {
        Payment payment = paymentService.getPaymentByRideId(rideId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    /**
     * Lists payments belonging to a customer. Customers may only list their own
     * payments; admins can list any customer's payments (IDOR protection).
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagedResponse<Payment>>> getCustomerPayments(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN") && !currentUserId.equals(customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to view these payments"));
        }
        Page<Payment> result = paymentService.getCustomerPayments(
                customerId, page, Math.min(size, 100));
        PagedResponse<Payment> body = new PagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
