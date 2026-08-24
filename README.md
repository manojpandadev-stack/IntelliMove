# IntelliMove — Enterprise Intelligent Mobility & Delivery Platform

A production-quality Uber-style ride-hailing platform built with Java 21, Spring Boot 3, React, and microservices architecture.

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
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        PostgreSQL         Redis        Elasticsearch
      (Per Service)    (GEO, Cache)    (Operational Search)
```

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Spring Cloud Gateway |
| **Messaging** | Apache Kafka with outbox pattern |
| **Databases** | PostgreSQL (per-service), Redis (GEO/location), Elasticsearch |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS, React Query |
| **AI** | Spring AI with tool-calling architecture |
| **Infrastructure** | Docker, Docker Compose, Kubernetes, GitHub Actions |
| **Observability** | Spring Boot Actuator, Prometheus, Grafana |
| **Resilience** | Resilience4j (circuit breaker, retry, timeout) |

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

### AI Operations Assistant
Controlled tool-calling architecture:
- `getRideStatistics()` — ride metrics
- `getDriverAvailability()` — online/available counts
- `getCancellationStatistics()` — cancellation analysis
- `getDemandStatistics()` — demand and surge data
- `getRevenueStatistics()` — financial metrics
- `searchIncidents()` — operational alerts

## Prerequisites

- Java 21
- Node.js 20+
- Docker & Docker Compose
- Maven 3.9+

## Quick Start

### 1. Start Infrastructure
```bash
docker compose up -d postgres redis kafka zookeeper elasticsearch
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

### Unit Tests
```bash
# Run all tests
mvn test

# Run specific module tests
mvn test -pl intellimove-ride
mvn test -pl intellimove-driver
mvn test -pl intellimove-payment
```

### E2E Tests
```bash
# Requires all services running locally on their default ports
bash e2e-test.sh

# Expected output: 32 passed, 0 failed
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

```bash
# Create namespace and secrets
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml

# Deploy services
kubectl apply -f k8s/gateway.yaml
kubectl apply -f k8s/auth-service.yaml
# (similar for other services)
```

## CI/CD

GitHub Actions pipeline (`.github/workflows/ci.yml`):
1. **Backend Build** — Compile, test, verify
2. **Frontend Build** — Install, type-check, build
3. **Security Scan** — Dependency check
4. **Docker Build** — Multi-service matrix build
5. **Deploy** — On merge to main

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

## Security

- JWT-based authentication with access/refresh tokens
- Role-based access control (SUPER_ADMIN, ADMIN, DRIVER, CUSTOMER, SUPPORT)
- Token blacklisting via Redis for logout
- Account lockout after 5 failed login attempts
- Service-to-service user ID forwarding via gateway
- Input validation on all endpoints
- Centralized exception handling (no stack traces exposed)
- Rate limiting on auth endpoints

## License

MIT
