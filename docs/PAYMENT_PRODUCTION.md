# IntelliMove — Payment Production Readiness

## Current Status: SANDBOX VERIFIED, PRODUCTION BLOCKED BY CREDENTIALS

## Architecture

```
PaymentService
    ↓
PaymentProvider (interface)
    ├── SandboxPaymentProvider (default, local testing)
    └── [Production Provider] (requires credentials)
```

## Provider Interface

```java
public interface PaymentProvider {
    record PaymentResult(boolean success, String providerTransactionId, String failureReason) {}
    
    PaymentResult initiatePayment(String idempotencyKey, BigDecimal amount, 
                                   String currency, String paymentMethod);
    PaymentResult confirmPayment(String providerTransactionId);
    PaymentResult refundPayment(String providerTransactionId, BigDecimal amount);
}
```

## State Machine

```
INITIATED → PROCESSING → COMPLETED
    ↓           ↓
  FAILED      FAILED
    ↓
INITIATED (retry)
    
COMPLETED → REFUNDED
```

## Sandbox Provider

- All payments succeed after a simulated delay
- No external API calls
- Used for development and testing
- Default when `PAYMENT_PROVIDER=sandbox`

## Production Provider Integration

To integrate a real payment provider:

1. Create a new class implementing `PaymentProvider`
2. Add provider-specific credentials as environment variables
3. Configure provider selection via `PAYMENT_PROVIDER` env var
4. Implement webhook signature verification
5. Add proper retry logic with exponential backoff

### Required Environment Variables (Production)

```bash
PAYMENT_PROVIDER=stripe  # or adyen, square, etc.
STRIPE_API_KEY=sk_live_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx
STRIPE_API_VERSION=2024-01-01
```

## Features Verified

| Feature | Status |
|---------|--------|
| Payment initiation | ✅ SANDBOX VERIFIED |
| Payment confirmation | ✅ SANDBOX VERIFIED |
| Payment failure handling | ✅ SANDBOX VERIFIED |
| Refund processing | ✅ SANDBOX VERIFIED |
| Idempotency (one per ride) | ✅ VERIFIED |
| State machine transitions | ✅ VERIFIED |
| Invalid state rejection (409) | ✅ VERIFIED |
| Duplicate webhook handling | ✅ VERIFIED |
| Event publishing | ✅ VERIFIED |
| Outbox integration | ✅ VERIFIED |

## Limitations

- No real payment provider is configured
- No webhook signature verification (sandbox doesn't need it)
- No retry with backoff (sandbox always succeeds)
- Production deployment requires payment provider credentials
