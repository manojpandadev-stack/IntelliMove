# IntelliMove — Kubernetes Deployment

## Current Status: MANIFESTS VALIDATED, RUNTIME BLOCKED (No Cluster)

## Manifests

| File | Resources |
|------|-----------|
| `k8s/namespace.yaml` | Namespace: intellimove |
| `k8s/configmap.yaml` | Environment configuration |
| `k8s/secrets.yaml` | Sensitive configuration (base64-encoded) |
| `k8s/services.yaml` | All backend service Deployments + Services |
| `k8s/gateway.yaml` | API Gateway Deployment + Service |
| `k8s/auth-service.yaml` | Auth Service Deployment + Service |

## Architecture

```
Ingress Controller
    ↓
Gateway Service (ClusterIP)
    ↓
┌───────┬───────┬───────┬───────┬───────┬───────┬───────┬───────┐
Auth    User   Driver  Ride   Location Payment Notif  AI-Ops
(ClusterIP services per service)
    ↓
PostgreSQL / Redis / Kafka (external or in-cluster)
```

## Resource Configuration

### Per Service

| Resource | Request | Limit |
|----------|---------|-------|
| CPU | 250m | 500m |
| Memory | 256Mi | 512Mi |

### Gateway

| Resource | Request | Limit |
|----------|---------|-------|
| CPU | 500m | 1000m |
| Memory | 512Mi | 1024Mi |

## Health Probes

All services configure:
- **Readiness probe**: `/actuator/health/readiness`
- **Liveness probe**: `/actuator/health/liveness`
- **Startup probe**: `/actuator/health`

## HPA (Horizontal Pod Autoscaler)

- Min replicas: 2
- Max replicas: 5
- CPU target: 70%

## PDB (Pod Disruption Budget)

- Min available: 1

## Environment Variables

Configured via ConfigMap and Secrets:
- `JWT_SECRET` (Secret)
- `POSTGRES_HOST`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (ConfigMap/Secret)
- `REDIS_HOST` (ConfigMap)
- `KAFKA_BROKERS` (ConfigMap)

## Deployment Commands

```bash
# Apply all manifests
kubectl apply -f k8s/

# Verify
kubectl get pods -n intellimove
kubectl get services -n intellimove

# Check health
kubectl describe deployment -n intellimove
```

## Limitations

- No cluster available for runtime verification
- PostgreSQL, Redis, Kafka assumed external or separately deployed
- No Ingress TLS configured
- No NetworkPolicy
- No ServiceAccount/RBAC for pods
