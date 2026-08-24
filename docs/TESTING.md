# IntelliMove — Testing Guide

## Test Summary

| Suite | Pass | Fail | Skip | Total |
|-------|------|------|------|-------|
| Java unit/integration | 88 | 0 | 25 | 113 |
| Playwright browser E2E | 23 | 0 | 0 | 23 |
| Shell E2E | 35 | 0 | 0 | 35 |
| **TOTAL** | **146** | **0** | **25** | **171** |

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

**Blocker**: Windows Docker Desktop named pipe not accessible from Java Testcontainers.

**Resolution**: Run on Linux CI/CD where Docker is natively accessible.

Tests cover:
- PostgreSQL: Flyway migrations, repository CRUD, transactions, constraints
- Redis: GEO storage, nearby search, distributed locks, idempotency
- Kafka: Producer, consumer, events, duplicate events

### Playwright Browser E2E

| Test | Role | Flow |
|------|------|------|
| Customer registration | CUSTOMER | Register → redirect to login |
| Customer login | CUSTOMER | Login → dashboard |
| Customer dashboard | CUSTOMER | Dashboard loads with correct content |
| Customer request ride | CUSTOMER | Request ride form works |
| Customer ride history | CUSTOMER | History page loads |
| Customer invalid login | CUSTOMER | Error message shown |
| Driver registration | DRIVER | Register → redirect |
| Driver login | DRIVER | Login → dashboard |
| Driver dashboard | DRIVER | Dashboard loads |
| Driver vehicle info | DRIVER | Vehicle form works |
| Driver go online | DRIVER | Status change works |
| Admin login | ADMIN | Login → dashboard |
| Admin dashboard | ADMIN | Dashboard loads with tabs |
| Admin users tab | ADMIN | User list loads |
| Admin drivers tab | ADMIN | Driver list loads |
| Admin rides tab | ADMIN | Ride list loads |
| Admin payments tab | ADMIN | Payment list loads |
| Navigation protection | ALL | Unauthorized redirect works |
| JWT token handling | ALL | Token storage/clear works |
| Role-based routing | ALL | Correct redirects per role |
| API error handling | ALL | Error states shown |
| Loading states | ALL | Loading indicators work |
| Responsive layout | ALL | Mobile/desktop layout |

### Shell E2E

35 tests covering:
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
