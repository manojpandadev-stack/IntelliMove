# IntelliMove — Security Audit

## Date: 2026-08-24

## Executive Summary

IntelliMove implements defense-in-depth security across all layers. Two header-forging vulnerabilities were identified and fixed during this audit. All HIGH severity findings from the original audit have been addressed.

---

## 1. Authentication

| Area | Status | Details |
|------|--------|---------|
| JWT generation | ✅ VERIFIED | HMAC-SHA with configurable secret |
| JWT validation | ✅ VERIFIED | Signature + expiration checked |
| JWT expiry | ✅ VERIFIED | Configurable via `jwt.expiration-ms` |
| Refresh tokens | ✅ VERIFIED | Separate token type, blacklisted on use |
| Password hashing | ✅ VERIFIED | BCrypt via Spring Security PasswordEncoder |
| Account lockout | ✅ VERIFIED | 5 failed attempts → 30-minute lockout |
| Token blacklisting | ✅ VERIFIED | Redis-based with TTL matching token expiry |

## 2. Authorization (RBAC)

| Role | Access |
|------|--------|
| CUSTOMER | Request rides, view own rides, AI support |
| DRIVER | Accept/complete rides, update location, AI support |
| ADMIN | Full access to all endpoints |
| SUPER_ADMIN | Full access, seed-data-only (not registrable) |

**SUPER_ADMIN registration prevention**: Public registration rejects SUPER_ADMIN role, defaulting to CUSTOMER.

## 3. IDOR Protection

| Endpoint | Protection |
|----------|------------|
| `GET /rides/{id}` | `isUserAuthorizedForRide()` check — customer or driver |
| `GET /rides/customer/{id}` | `currentUserId.equals(customerId)` or ADMIN |
| `GET /rides/driver/{id}` | `currentUserId.equals(driverId)` or ADMIN |
| `POST /rides` (request) | Identity from `SecurityUtils.getCurrentUserId()` |
| `POST /rides/{id}/accept` | Identity from JWT, verified as assigned driver |
| `POST /rides/{id}/start` | Identity from JWT, verified as assigned driver |
| `POST /rides/{id}/complete` | Identity from JWT, verified as assigned driver |

## 4. Header-Forging Prevention (FIXED)

**Previously found and fixed:**

| Controller | Old Pattern | Fixed Pattern |
|------------|-------------|---------------|
| `DriverController.registerDriver()` | `@RequestHeader("X-User-Id")` | `SecurityUtils.getCurrentUserId()` |
| `AiSupportController.processQuery()` | `@RequestHeader("X-User-Id")` | `SecurityUtils.getCurrentUserIdString()` |

**Architecture**: `SecurityUtils` extracts identity from the JWT principal set by `JwtAuthenticationFilter`, NOT from client-supplied headers. This prevents identity forgery when services are accessed directly (bypassing the API Gateway).

## 5. WebSocket Security

| Area | Status | Details |
|------|--------|---------|
| Authentication at handshake | ✅ VERIFIED | JWT validated in WebSocketConfig |
| Session identity | ✅ VERIFIED | userId + roles from session attributes |
| Subscription authorization | ✅ VERIFIED | `RideValidationService.isUserAuthorizedForRide()` |
| Cross-user data leakage | ✅ BLOCKED | Database-backed authorization check |
| Disconnect cleanup | ✅ VERIFIED | Sessions + subscriptions cleaned on close |
| One subscription per session | ✅ VERIFIED | New subscription replaces old |

## 6. API Security

| Area | Status | Details |
|------|--------|---------|
| Rate limiting | ✅ IMPLEMENTED | Gateway rate limiter |
| Input validation | ✅ IMPLEMENTED | `@Valid` + `@NotBlank` on all endpoints |
| CORS | ✅ CONFIGURED | Gateway CORS configuration |
| Exception handling | ✅ VERIFIED | GlobalExceptionHandler, no stack traces |
| Error trace IDs | ✅ VERIFIED | UUID traceId in all error responses |
| Sensitive error messages | ✅ VERIFIED | "An unexpected error occurred" for 500s |

## 7. Data Security

| Area | Status | Details |
|------|--------|---------|
| Password storage | ✅ VERIFIED | BCrypt hash, never plaintext |
| JWT secret | ✅ CONFIGURABLE | Environment variable, not hardcoded |
| Database passwords | ✅ CONFIGURABLE | Environment variables |
| Redis | ✅ VERIFIED | Token blacklisting, GEO, caching |
| Sensitive data in logs | ✅ VERIFIED | No passwords, no tokens logged |

## 8. Payment Security

| Area | Status | Details |
|------|--------|---------|
| Idempotency | ✅ VERIFIED | One payment per ride |
| State machine | ✅ VERIFIED | Valid transitions enforced |
| Duplicate prevention | ✅ VERIFIED | `findByRideId()` check |
| Refund validation | ✅ VERIFIED | Amount ≤ original payment |
| Webhook handling | ✅ VERIFIED | Idempotent for completed payments |

## 9. AI Security

| Area | Status | Details |
|------|--------|---------|
| Tool authorization | ✅ VERIFIED | ADMIN role required for AI Ops |
| Customer isolation | ✅ VERIFIED | AI Support scoped to authenticated user |
| No arbitrary SQL | ✅ VERIFIED | Tools return aggregated data only |
| No arbitrary commands | ✅ VERIFIED | No shell execution tools |
| No unrestricted DB access | ✅ VERIFIED | Pre-built read-only queries |
| Unauthorized data access | ✅ VERIFIED | Role-based tool access |
| Timeout handling | ✅ VERIFIED | CompletableFuture 30s timeout |
| LLM failure fallback | ✅ VERIFIED | Keyword routing returns tool results |

## 10. Input Validation

| Area | Status | Details |
|------|--------|---------|
| `@Valid` on request bodies | ✅ | All POST/PATCH endpoints |
| `@NotBlank` / `@NotNull` | ✅ | Required fields |
| `ConstraintViolationException` handler | ✅ | Returns HTTP 400 |
| `MethodArgumentNotValidException` handler | ✅ | Returns field-level errors |
| `HttpMessageNotReadableException` handler | ✅ | Returns invalid body details |
| SQL injection | ✅ PROTECTED | JPA parameterized queries only |

## 11. Service-to-Service Security

| Area | Status | Details |
|------|--------|---------|
| Gateway forwards JWT identity | ✅ VERIFIED | X-User-Id from JWT claims |
| Downstream uses JWT principal | ✅ VERIFIED | SecurityUtils, not headers |
| Internal endpoints | ⚠️ | `/internal/*` endpoints have no RBAC — network-isolated only |
| Direct port access | ✅ PROTECTED | JwtAuthenticationFilter on all services |

## 12. Findings Summary

### FIXED in This Audit
1. **[HIGH] DriverController header forging** — `@RequestHeader("X-User-Id")` → `SecurityUtils.getCurrentUserId()`
2. **[HIGH] AiSupportController header forging** — `@RequestHeader("X-User-Id")` → `SecurityUtils.getCurrentUserIdString()`
3. **[LOW] Docker compose version deprecation** — Removed `version: '3.8'`

### Previously Fixed (from prior hardening phases)
- IDOR on ride/driver endpoints
- WebSocket authentication
- Account lockout
- Token blacklisting
- Password hashing
- Input validation
- Exception handling

### Accepted Risks
- Internal endpoints (`/internal/*`) rely on network isolation — acceptable for internal services
- Prometheus content-negotiation issue is environment-specific, not a security concern

## 13. Regression Tests

| Test | Count | Status |
|------|-------|--------|
| SecurityRegressionTest | 20 | ✅ ALL PASS |
| ConcurrencyTest | 6 | ✅ ALL PASS |
| **Total security-related tests** | **26** | **✅ 0 failures** |

Tests cover:
- Invalid JWT → 401
- Missing token → 401
- Expired token → 401
- Wrong role → 403
- IDOR customer → customer → FORBIDDEN
- IDOR customer → driver → FORBIDDEN
- IDOR driver → customer → FORBIDDEN
- Admin access → ALLOWED
- Concurrent driver assignment → only one succeeds
- Duplicate payment → idempotent
- Invalid state transition → 409
