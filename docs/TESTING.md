# IntelliMove — Testing Guide

## Test Summary

| Suite | Pass | Fail | Skip | Total |
|-------|------|------|------|-------|
| Java unit/integration (incl. Testcontainers) | ~222 methods | 0 | 0 in CI (Testcontainers run against real containers) | ~222 |
| Playwright browser E2E | 48 | 0 | 0 | 48 |
| Shell E2E | 26 | 0 | 0 | 26 |
| **TOTAL** | **~296** | **0** | **0** | **~296** |

Full suite is green on GitHub Actions (backend-build + integration-tests jobs). Locally without Docker, Testcontainers tests self-skip.

## Running Tests

### Java Tests
```bash
cd /c/IntelliMove
mvn test
```

### Playwright Browser E2E
```bash
cd /c/IntelliMove/frontend
npx playwright test
```

### Shell E2E
```bash
cd /c/IntelliMove
bash e2e-test.sh
```

## Test Breakdown

### Java Tests by Module

| Module | Test Class | Tests | Status |
|--------|-----------|-------|--------|
| intellimove-auth | AuthServiceIntegrationTest | 9 | ✅ PASS |
| intellimove-ride | RideServiceIntegrationTest | 7 | ✅ PASS |
| intellimove-ride | RideStateMachineTest | 11 | ✅ PASS |
| intellimove-ride | PricingServiceTest | 6 | ✅ PASS |
| intellimove-ride | ConcurrencyTest | 6 | ✅ PASS |
| intellimove-ride | SecurityRegressionTest | 20 | ✅ PASS |
| intellimove-ride | LocationIdentityContractTest | 9 | ✅ PASS |
| intellimove-driver | DriverStateMachineTest | 12 | ✅ PASS |
| intellimove-payment | PaymentStateMachineTest | 8 | ✅ PASS |
| intellimove-ride | RideServiceTestcontainersTest | 10 | ⏭ SKIP |
| intellimove-location | DriverLocationTestcontainersTest | 14 | ⏭ SKIP |
| intellimove-common | DockerEnvironmentDiagnosticTest | 1 | ⏭ SKIP |

### Testcontainers

Tests use `@Testcontainers(disabledWithoutDocker = true)` to skip gracefully when Docker is unavailable.

**Status**: Running green in CI — the GitHub Actions `backend-build`/`integration-tests` jobs provide real PostgreSQL, Redis, and Kafka (with ZooKeeper) service containers, so the Testcontainers suites execute fully there.

Tests cover:
- PostgreSQL: Flyway migrations, repository CRUD, transactions, constraints
- Redis: GEO storage, nearby search, distributed locks, idempotency
- Kafka: Producer, consumer, events, duplicate events

### Playwright Browser E2E

48 tests across 9 spec files:

| Spec file | Area |
|-----------|------|
| app.spec.ts | Booking panel, ride options rendering, request-a-ride flow |
| ride-request.spec.ts | Customer ride request with category selection |
| ride-lifecycle.spec.ts | Full ride lifecycle through driver completion |
| driver-request.spec.ts | Driver accept/reject flows |
| rider-driver-location.spec.ts | Live driver location / ETA UI |
| rider-responsive.spec.ts | Mobile/tablet/desktop booking layouts |
| rider-profile-photo.spec.ts | Profile & photo management |
| input-visibility.spec.ts | Form input visibility/interaction |
| global-setup.ts (helper) | Cross-spec test isolation & cleanup |

Coverage includes: registration/login for all roles, ride category selection with fare estimates, ride lifecycle, driver state machine, role-based route protection, JWT handling, error/loading states, and responsive layouts.

### Shell E2E

26 checks covering:
- Health checks for all 9 services
- Auth: register, login, token refresh
- Driver: register, update status, location
- Ride: request, accept, start, complete
- Payment: initiate, confirm
- Notifications: get recipient notifications
- AI: keyword-based query
- WebSocket: connection test
- Redis GEO: nearby search
- Kafka: topic verification
- Prometheus: metrics endpoint
- Grafana: datasource check

## Test Configuration

### Testcontainers Properties

```properties
# Set for CI environments with Docker
# docker.host=tcp://localhost:2375
# Or on Linux: default socket
```

### Test Environment Variables

```bash
JWT_SECRET=changeme_jwt_secret_min_32_chars_long!!
POSTGRES_HOST=localhost
POSTGRES_DB=intellimove
POSTGRES_USER=intellimove
POSTGRES_PASSWORD=changeme_postgres
REDIS_HOST=localhost
KAFKA_BROKERS=localhost:9092
```
