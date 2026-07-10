# FinTrack

Household personal-finance tracker. Learning project covering Spring Boot 4 microservices, Postgres, Docker, Kubernetes (Minikube → GKE), React, and Claude AI.

Docs: [architecture](docs/ARCHITECTURE.md) · [roadmap](docs/ROADMAP.md) · [decisions](docs/decisions/)

## Layout

```
services/auth-service/   Spring Boot 4.1 · Java 25 — identity, JWT, households
frontend/                React 19 + Vite + TS (phase 3)
infra/                   Docker Compose now, K8s manifests + Helm later
docs/                    Architecture, roadmap, ADRs
```

## Prerequisites (one-time)

```bash
# SDKMAN manages Java/Gradle versions
curl -s "https://get.sdkman.io" | bash
sdk install java 25-tem
sdk install gradle

# Docker Desktop must be installed and running
```

## Bootstrap (one-time, after clone)

The Gradle wrapper jar is not committed by scaffolding; generate it once:

```bash
cd services/auth-service
gradle wrapper
```

## Run locally

```bash
cp .env.example .env                 # adjust if needed
docker compose up -d postgres        # database
cd services/auth-service
./gradlew bootRun                    # http://localhost:8081
```

Verify: `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`

## Test

```bash
cd services/auth-service
./gradlew test          # integration tests use Testcontainers (needs Docker running)
```

## Working with Claude

Open this folder in Claude Code (`claude` from the repo root) or Cowork. `CLAUDE.md` gives Claude the project context and conventions.
