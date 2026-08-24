# IntelliMove — CI/CD Pipeline

## Overview

GitHub Actions pipeline with 6 stages:

```
checkout → build → test → security → docker → artifact
```

## Pipeline Stages

### 1. Backend Build & Unit Test
- JDK 21 setup
- Maven cache
- `mvn clean test`
- Upload surefire reports as artifacts

### 2. Integration Tests (Testcontainers)
- PostgreSQL service container
- Redis service container
- Kafka service container
- `mvn verify` with real containers
- Upload failsafe reports as artifacts

### 3. Frontend Build & Test
- Node.js 20 setup
- npm cache
- TypeScript type check
- Production build

### 4. Security Scan
- Dependency vulnerability check
- Hardcoded secret scan (sk_live, sk_test, AKIA)
- Continues on non-critical findings

### 5. Docker Build (main branch only)
- Multi-service matrix build
- Docker Buildx for caching
- Tags: `git-sha` + `latest`

### 6. Frontend Docker Build (main branch only)
- Production nginx build
- Tags: `git-sha` + `latest`

## Triggers

| Event | Action |
|-------|--------|
| Push to main | Full pipeline + Docker build |
| Push to develop | Build + test + security |
| Pull request to main | Build + test + security |

## Environment

- Runner: `ubuntu-latest`
- Java: 21 (Temurin)
- Node.js: 20
- Docker: Buildx

## Adding New Services

1. Create `docker/Dockerfile.<service>`
2. Add to the `matrix.service` list in `.github/workflows/ci.yml`
3. Ensure the service has a health endpoint at `/actuator/health`
