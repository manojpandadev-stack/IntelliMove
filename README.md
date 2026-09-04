# IntelliMove — Enterprise Intelligent Mobility & Delivery Platform

[![CI/CD](https://github.com/manojpandadev-stack/IntelliMove/actions/workflows/ci.yml/badge.svg)](https://github.com/manojpandadev-stack/IntelliMove/actions/workflows/ci.yml)

A full-stack Uber-style ride-hailing platform built with Java 21, Spring Boot 3, React, and a microservices architecture.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          React Frontend                             │
│  (Customer / Driver / Admin Dashboards + AI Assistant)              │
└─────────────────────────────┬───────────────────────────────────────┘
                              │ HTTP / WebSocket
┌─────────────────────────────▼───────────────────────────────────────┐
│                        API Gateway (:8080)                          │
│  (JWT Auth, Rate Limiting, Request Routing)                         │
└──┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬───────────────┘
   │      │      │      │      │      │      │      │
   ▼      ▼      ▼      ▼      ▼      ▼      ▼      ▼
 Auth   User  Driver  Ride  Location Payment Notif  AI-Ops
 :8081  :8082  :8083  :8084  :8085   :8086  :8087  :8088
   │      │      │      │      │      │      │      │
   └──────┴──────┴──────┴──────┴──────┴──────┴──────┘
                              │
                    ┌─────────▼─────────┐
                    │   Apache Kafka    │
                    │  (Event Bus)      │
                    └─────────┬─────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
        PostgreSQL                        Redis
      (Per Service)            (GEO, Cache, Token Blacklist)
```

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Spring Cloud Gateway |
| **Messaging** | Apache Kafka with outbox pattern |
| **Databases** | PostgreSQL (per-service), Redis (GEO/location, cache, token blacklist) |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS, React Query |
| **AI** | Spring AI with tool-calling architecture (keyword routing by default) |
| **Infrastructure** | Docker, Docker Compose, GitHub Actions, Kubernetes (reference manifests) |
| **Observability** | Spring Boot Actuator, Prometheus, Grafana |

## Microservices

| Service | Port | Description |
|---------|------|-------------|
| **API Gateway** | 8080 | Request routing, JWT validation, rate limiting |
| **Auth Service** | 8081 | Registration, login, JWT tokens, token blacklisting |
| **User Service** | 8082 | Customer profile management, search |
| **Driver Service** | 8083 | Driver registration, state machine, vehicle management |
| **Ride Service** | 8084 | Ride lifecycle, state machine, pricing engine |
| **Location Service** | 8085 | Real-time tracking (WebSocket), Redis GEO, driver matching |
| **Payment Service** | 8086 | Payment processing, provider abstraction, refunds, idempotency |
| **Notification Service** | 8087 | Email, push, in-app notifications via Kafka |
| **AI Operations** | 8088 | AI-powered ops assistant with controlled tool calling |

## Key Features

### Ride Lifecycle State Machine
```
REQUESTED → MATCHING → DRIVER_ASSIGNED → DRIVER_ACCEPTED
    → DRIVER_ARRIVING → TRIP_STARTED → TRIP_COMPLETED
Any active state can → CANCELLED
```

### Driver State Machine
```
OFFLINE → ONLINE → AVAILABLE → OFFERED → ON_TRIP
OFFLINE/SUSPENDED (locked)
```

### Payment Flow (Saga)
```
Ride Completed → Payment Initiated → Payment Processing
    → Payment Completed → (success)
    → Payment Failed → Retry / Compensation
```

### Driver Matching Algorithm
Multi-factor scoring:
- **Distance** (40%): Closer drivers scored higher
- **Rating** (30%): Higher-rated drivers preferred
- **Experience** (20%): More trips = higher score
- **Fairness** (10%): Penalizes drivers with many recent trips

Distributed locking via Redis prevents race conditions.

### Real-Time Driver Location & ETA
- WebSocket endpoint streams live driver GPS positions to the frontend map.
- Redis GEO stores driver locations and powers proximity-based driver matching.
- Pickup ETA is computed from the haversine distance to the nearest driver and the configured urban driving-speed assumption (`location.eta.speed-kmh`), exposed via `GET /api/v1/rides/eta`.

### AI Operations Assistant
Controlled tool-calling architecture:
- `getRideStatistics()` — ride metrics
- `getDriverAvailability()` — online/available counts
- `getCancellationStatistics()` — cancellation analysis
- `getDemandStatistics()` — demand and surge data
- `getRevenueStatistics()` — financial metrics
- `searchIncidents()` — operational alerts

## Live Demo / Screenshots

A complete ride-hailing journey was verified **end-to-end against the real local
Docker Compose stack** (all 16 containers healthy). A customer and a driver both
registered and logged in through the live UI, and ride matching and the full ride
lifecycle ran against the live Kafka/Redis backend (no mocked ride data). The
screenshots below are real captures from that running instance.

**Demo workflow:** Register/Login → Fare Estimate → Request Ride → Driver Matching → Driver Accept → Live Location/ETA → Complete Ride → Payment/Notification → Admin/AI.

| Step | Screenshot |
|------|------------|
| Login / registration screen | [docs/screenshots/01-login-register.png](docs/screenshots/01-login-register.png) |
| Customer dashboard with live map | [docs/screenshots/02-customer-dashboard-map.png](docs/screenshots/02-customer-dashboard-map.png) |
| Ride category fare cards (Economy / Comfort / Premium / XL) | [docs/screenshots/03-ride-categories-fares.png](docs/screenshots/03-ride-categories-fares.png) |
| Active ride with driver marker + live ETA | [docs/screenshots/04-active-ride-live-eta.png](docs/screenshots/04-active-ride-live-eta.png) |
| Driver receives the incoming ride request | [docs/screenshots/05-driver-ride-request.png](docs/screenshots/05-driver-ride-request.png) |
| Driver accepts the ride | [docs/screenshots/05b-driver-accepted.png](docs/screenshots/05b-driver-accepted.png) |
| Admin / AI support console | [docs/screenshots/06-admin-ai-support.png](docs/screenshots/06-admin-ai-support.png) |
| Payment sandbox record | [docs/screenshots/07-payment-record.png](docs/screenshots/07-payment-record.png) |

> **Notes:** Payment uses the **sandbox provider** (simulated success/failure; no
> production gateway credentials — see [docs/PAYMENT_PRODUCTION.md](docs/PAYMENT_PRODUCTION.md)).
> The AI assistant runs in **keyword-routing mode by default** with an optional
> pluggable LLM — no LLM required (see [docs/AI_PRODUCTION.md](docs/AI_PRODUCTION.md)).
> These captures are from local development and are illustrative of functionality.

## Prerequisites

- Java 21
- Node.js 20+
- Docker & Docker Compose
- Maven 3.9+

## Quick Start

### 1. Start Infrastructure
```bash
docker compose up -d postgres redis kafka zookeeper prometheus grafana
```

### 2. Build Backend
```bash
mvn clean package -DskipTests
```

### 3. Start Services
```bash
# Start each service (or use Docker Compose)
java -jar intellimove-auth/target/*.jar &
java -jar intellimove-user/target/*.jar &
java -jar intellimove-driver/target/*.jar &
java -jar intellimove-ride/target/*.jar &
java -jar intellimove-location/target/*.jar &
java -jar intellimove-payment/target/*.jar &
java -jar intellimove-notification/target/*.jar &
java -jar intellimove-ai-ops/target/*.jar &
java -jar intellimove-gateway/target/*.jar &
```

### 4. Start Frontend
```bash
cd frontend && npm install && npm run dev
```

### 5. Full Docker Stack
```bash
docker compose up -d
```

## Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Key variables:
- `POSTGRES_PASSWORD` — Database password
- `JWT_SECRET` — JWT signing secret (min 32 chars)
- `SPRING_AI_API_KEY` — OpenAI API key for AI features
- `KAFKA_BROKERS` — Kafka broker addresses

## API Examples

### Register
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123","firstName":"John","lastName":"Doe"}'
```

### Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

### Request Ride
```bash
curl -X POST http://localhost:8080/api/v1/rides \
  -H "Authorization: Bearer <token>" \
  -H "X-User-Id: <customer-id>" \
  -H "Content-Type: application/json" \
  -d '{
    "rideType": "ECONOMY",
    "pickupLatitude": 40.7128,
    "pickupLongitude": -74.006,
    "dropoffLatitude": 40.7580,
    "dropoffLongitude": -73.9855,
    "pickupAddress": "New York, NY",
    "dropoffAddress": "Times Square, NY"
  }'
```

### AI Operations Query
```bash
curl -X POST http://localhost:8080/api/v1/ai/ops/query \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"query": "Why did cancellations increase today?"}'
```

## Testing

~222 backend test methods across the 9 services, plus 48 Playwright browser E2E tests and 26 shell-based API E2E checks. The full suite runs green in GitHub Actions.

### Unit & Integration Tests
```bash
# Run all tests (Testcontainers tests require Docker and run in CI)
mvn test

# Run specific module tests
mvn test -pl intellimove-ride
mvn test -pl intellimove-driver
mvn test -pl intellimove-payment
```

Testcontainers suites (`RideServiceTestcontainersTest`, `DriverLocationTestcontainersTest`, matching concurrency ITs) run against real PostgreSQL, Redis, and Kafka containers in CI, covering Flyway migrations, repository behavior, Redis GEO search, distributed locks, and Kafka producer/consumer flows. They self-skip when Docker is unavailable.

### Browser E2E (Playwright)
```bash
cd frontend && npx playwright install --with-deps
cd frontend && npx playwright test
```
48 tests in 9 specs covering customer/driver/admin flows: registration, login, ride request with category selection, full ride lifecycle, driver accept/reject, live location/ETA UI, profile & saved places, responsive/mobile layouts, and role-based route protection.

### Shell API E2E
```bash
# Requires all services running locally on their default ports
bash e2e-test.sh

# Expected output: all checks pass (0 failures)
```

The E2E test covers:
1. All 9 service health checks
2. Customer, Driver, Admin registration
3. Login and JWT validation
4. Driver profile creation and state machine
5. Redis GEO location storage and search
6. Complete ride lifecycle (REQUESTED → TRIP_COMPLETED)
7. Payment state machine
8. Invalid state transition rejection
9. AI Operations query

### Frontend
```bash
cd frontend && npm install && npm run dev
# Visit http://localhost:5173

# Type checking
cd frontend && npx tsc --noEmit

# Build
cd frontend && npm run build
```

## Kubernetes Deployment

> **Status:** Kubernetes manifests are provided and structurally validated for reference
> purposes. IntelliMove has **not** been deployed to a live Kubernetes cluster, and no
> container images have been pushed to a registry.

```bash
# Create namespace, then create secrets from the .env.example template
# (k8s/secrets.yaml is intentionally not committed — generate it locally first)
kubectl apply -f k8s/namespace.yaml
kubectl create secret generic intellimove-secrets --from-env-file=.env.example -n intellimove
kubectl apply -f k8s/configmap.yaml

# Deploy services (see docs/DEPLOYMENT.md for the full manifest set)
kubectl apply -f k8s/gateway.yaml
kubectl apply -f k8s/auth-service.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/hpa.yaml
```

## CI/CD

GitHub Actions pipeline (`.github/workflows/ci.yml`):
1. **Backend Build** — Compile and run unit tests (JDK 21)
2. **Integration Tests** — Postgres/Redis/Kafka services on Linux for integration suite
3. **Frontend Build** — Install, type-check, build
4. **Security Scan** — Dependency vulnerability check + hardcoded-secret grep
5. **Docker Build** — Builds all 9 service images + frontend image on merge to main (no registry push / no deploy)

## Monitoring

- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3001` (admin/admin)
- **Kafka UI**: `http://localhost:8089`

## Database Schema

Each service has its own database with Flyway migrations:
- `intellimove_auth` — Auth users, roles
- `intellimove_user` — User profiles
- `intellimove_driver` — Driver details, vehicles
- `intellimove_ride` — Rides, outbox events
- `intellimove_payment` — Payments, refunds

## Kafka Topics

| Topic | Events |
|-------|--------|
| `ride-events` | RIDE_REQUESTED, DRIVER_ASSIGNED, DRIVER_ACCEPTED, RIDE_STARTED, RIDE_COMPLETED, RIDE_CANCELLED |
| `payment-events` | PAYMENT_INITIATED, PAYMENT_COMPLETED, PAYMENT_FAILED, REFUND_INITIATED |
| `notification-events` | NOTIFICATION_REQUESTED (for all notification types) |
| `driver-events` | DRIVER_STATUS_CHANGED |
| `user-events` | USER_REGISTERED |

## Security

- JWT-based authentication with access/refresh tokens
- Role-based access control (SUPER_ADMIN, ADMIN, DRIVER, CUSTOMER, SUPPORT)
- Token blacklisting via Redis for logout
- Account lockout after 5 failed login attempts
- Service-to-service user ID forwarding via gateway
- Input validation on all endpoints
- Centralized exception handling (no stack traces exposed)
- Rate limiting on auth endpoints

## Current Status & Limitations

**Deployment**
- Fully functional in local development via Docker Compose + `mvn`/`npm` (see
  [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)). Not deployed to any public cloud or
  production environment. Kubernetes manifests are provided but **not** deployed to a
  live cluster; no container images are published to a registry.

**Payments**
- Payment service uses a **sandbox provider** (simulated success/failure) with a pluggable
  `PaymentProvider` interface and idempotency. No production Stripe/Adyen/Square integration
  is configured — a real provider requires API credentials (see
  [docs/PAYMENT_PRODUCTION.md](docs/PAYMENT_PRODUCTION.md)).

**AI Operations**
- Runs in **keyword-routing mode by default** (no LLM required). An LLM provider
  (Ollama or an OpenAI-compatible API) is optional and disabled unless `LLM_ENABLED=true`
  (see [docs/AI_PRODUCTION.md](docs/AI_PRODUCTION.md)).

**Notifications**
- In-app notifications are implemented and verified. Email (SMTP) is configurable but
  requires credentials (see [docs/NOTIFICATION_PRODUCTION.md](docs/NOTIFICATION_PRODUCTION.md)).

**Resilience**
- Resilience4j is declared as a shared dependency but circuit-breaker/retry annotations are
  not yet wired into the application.

**Search**
- Elasticsearch is **not** part of the runtime stack; it only appears as an unused
  Testcontainers artifact in the parent POM.

## Repository Structure

```
IntelliMove/
├── intellimove-common/       # Shared events, outbox, DTOs, exception handling
├── intellimove-auth/         # JWT auth, refresh, revocation (Redis blacklist)
├── intellimove-user/         # Profiles, saved places, preferences, provisioning
├── intellimove-driver/       # Driver profile, vehicle, state machine
├── intellimove-ride/         # Ride lifecycle, pricing engine, fare estimation
├── intellimove-location/     # Redis GEO, matching, WebSocket, live ETA
├── intellimove-payment/      # Sandbox payment provider, saga, idempotency
├── intellimove-notification/ # In-app notifications via Kafka consumers
├── intellimove-ai-ops/       # AI support assistant (pluggable LLM provider)
├── intellimove-gateway/      # Spring Cloud Gateway + JWT filter
├── frontend/                 # React 19 + TypeScript + Vite + Playwright
├── docker/                   # Per-service Dockerfiles (Maven builder + JRE runtime)
├── k8s/                      # Kubernetes manifests (reference, not deployed)
├── monitoring/               # Prometheus config + Grafana provisioning
└── .github/workflows/        # CI/CD pipeline
```

## License

MIT
