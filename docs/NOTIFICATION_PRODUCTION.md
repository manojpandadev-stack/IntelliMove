# IntelliMove — Notification Production Readiness

## Current Status: IN-APP VERIFIED, SMTP BLOCKED BY CREDENTIALS

## Architecture

```
Ride Events (Kafka) ──→ NotificationService ──→ NotificationChannel(s)
                         │                         ├── InAppNotificationChannel (VERIFIED)
Payment Events (Kafka) ──→│                         └── EmailNotificationChannel (CONFIGURABLE)
                          │
Notification Events ─────→│
(Kafka)                    │
                           ↓
                    NotificationRepository (PostgreSQL)
```

## Event Flow

| Event | Source Topic | Notification |
|-------|-------------|--------------|
| Ride Requested | ride-events | "Your ride request has been received" |
| Driver Assigned | ride-events | "A driver has been assigned to your ride" |
| Driver Accepted | ride-events | "Your driver has accepted the ride" |
| Trip Started | ride-events | "Your trip has started" |
| Ride Completed | ride-events | "Your ride has been completed. Fare: $X.XX" |
| Ride Cancelled | ride-events | "Ride cancelled. Reason: ..." |
| Payment Completed | payment-events | Via notification-events topic |
| Payment Failed | payment-events | Via notification-events topic |

## Kafka Topics

| Topic | Purpose | Consumer Group |
|-------|---------|----------------|
| ride-events | Ride lifecycle events | notification-service |
| notification-events | Direct notification requests | notification-service |
| payment-events | Payment state changes | notification-service |

## In-App Notifications

- Persisted to PostgreSQL
- Read/unread status
- Recipient-scoped queries
- Unread count endpoint

## Email Notifications (SMTP)

### Configuration

```bash
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=user@example.com
SMTP_PASSWORD=secure_password
SMTP_FROM=noreply@intellimove.com
SMTP_ENABLED=true
```

### When SMTP is NOT configured:
- Email channel is disabled
- In-app notifications still work
- Notification failure does NOT break ride completion

## Resilience

| Aspect | Status |
|--------|--------|
| Notification failure breaks ride | ✅ NO — caught and logged |
| Duplicate event handling | ✅ IDEMPOTENT — saved with tracking |
| Channel failure isolation | ✅ VERIFIED — each channel tried independently |
| Kafka consumer error handling | ✅ VERIFIED — individual event failures logged |

## Limitations

- SMTP credentials not configured in current environment
- Email delivery not verified (requires SMTP provider)
- No push notifications (mobile)
- No SMS notifications
