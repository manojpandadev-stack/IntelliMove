# IntelliMove — Observability

## Current Status: PROMETHEUS + GRAFANA VERIFIED

## Components

| Component | Version | Status | Port |
|-----------|---------|--------|------|
| Prometheus | v2.39.0 | ✅ RUNNING | 9090 |
| Grafana | v10.3.1 | ✅ RUNNING | 3001 |
| Spring Boot Actuator | 3.x | ✅ RUNNING | per-service |
| Micrometer | 3.x | ✅ RUNNING | per-service |

## Metrics Exposed

### Per-Service (via /actuator/prometheus)

| Metric | Description |
|--------|-------------|
| `jvm_memory_used_bytes` | JVM heap/non-heap memory usage |
| `jvm_memory_max_bytes` | JVM memory limits |
| `jvm_gc_pause_seconds` | GC pause times |
| `jvm_threads_live_threads` | Live thread count |
| `http_server_requests_seconds` | HTTP request latency by endpoint |
| `http_server_requests_seconds_count` | HTTP request count |
| `http_server_requests_seconds_sum` | Total HTTP request time |
| `process_uptime_seconds` | Service uptime |
| `hikaricp_connections_active` | Active DB connections |
| `hikaricp_connections_idle` | Idle DB connections |
| `data_source_connections_active` | DataSource connections |

### Prometheus Scrape Configuration

- Scrape interval: 15s
- 8 backend services scraped (auth, user, driver, ride, location, payment, notification, ai-ops)
- PrometheusAcceptFilter normalizes Accept headers for Spring Boot 3.x compatibility

## Grafana Dashboard

### IntelliMove Overview

11 panels showing real metrics:
1. Active JVM Memory (all services)
2. HTTP Request Rate (all services)
3. HTTP Response Time p95
4. HTTP Error Rate (5xx)
5. JVM Thread Count
6. GC Pause Time
7. Service Uptime
8. Active DB Connections
9. CPU Usage
10. Process Start Time
11. HTTP Status Code Distribution

## Alerting (Recommended)

| Alert | Condition | Severity |
|-------|-----------|----------|
| Service Down | `up == 0` for 1+ minute | Critical |
| High 5xx Rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1` | High |
| High Latency | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2` | Medium |
| JVM Memory High | `jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85` | Medium |
| GC Pause High | `rate(jvm_gc_pause_seconds_sum[5m]) > 0.5` | Low |
| DB Connection Pool Exhausted | `hikaricp_connections_active == hikaricp_connections_max` | High |

## Startup Commands

```bash
# Prometheus
docker compose up -d prometheus
# Access: http://localhost:9090

# Grafana
docker compose up -d grafana
# Access: http://localhost:3001 (admin/admin)
```

## Limitations

- No distributed tracing (OpenTelemetry/Jaeger not configured)
- No custom business metrics (ride count, matching latency, etc.)
- No alerting rules configured
- Prometheus v2.39.0 (downgraded from v2.49 for Spring Boot 3.x compatibility)
- Kafka consumer lag metrics not yet exposed
