# IntelliMove — Deployment Guide

## Quick Start (Local Development)

### Prerequisites
- Java 21
- Node.js 20+
- Docker Desktop
- Maven 3.9+

### 1. Start Infrastructure
```bash
docker compose up -d postgres redis kafka zookeeper prometheus grafana
```

Wait for health checks to pass (~30 seconds).

### 2. Start Backend Services
```bash
bash start-services.sh
```

Wait 120 seconds for all services to boot.

### 3. Verify Services
```bash
for p in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
  curl -s -o /dev/null -w "Port $p: %{http_code}\n" http://localhost:$p/actuator/health
done
```

### 4. Start Frontend
```bash
cd frontend && npm run dev
```

### 5. Access
- Frontend: http://localhost:5173
- Gateway API: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (admin/admin)

## Docker Compose (Full Stack)

```bash
docker compose up -d
```

This starts: PostgreSQL, Redis, Kafka, Zookeeper, Prometheus, Grafana, all 9 backend services, frontend.

## Kubernetes

### Prerequisites
- kubectl configured
- Container images built and pushed to a registry

### Deploy
```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/gateway.yaml
kubectl apply -f k8s/auth-service.yaml
kubectl apply -f k8s/hpa.yaml
```

### Verify
```bash
kubectl get pods -n intellimove
kubectl get services -n intellimove
kubectl get hpa -n intellimove
```

## Environment Variables

See `.env.example` for the complete list of configurable environment variables.

### Required for Production
| Variable | Description |
|----------|-------------|
| JWT_SECRET | HMAC signing key (≥32 chars) |
| POSTGRES_PASSWORD | Database password |
| REDIS_PASSWORD | Redis password (if auth enabled) |

### Optional
| Variable | Default | Description |
|----------|---------|-------------|
| LLM_ENABLED | false | Enable AI LLM provider |
| PAYMENT_PROVIDER | sandbox | Payment provider selection |
| SMTP_HOST | localhost | Email server |

## Running Tests

```bash
# Java unit + integration tests
mvn test

# Playwright browser E2E
cd frontend && npx playwright test

# Shell E2E (requires running services)
bash e2e-test.sh
```
