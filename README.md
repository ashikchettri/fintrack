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

## Bootstrap

Nothing to do — the Gradle wrapper is committed, so `./gradlew` works from a fresh clone (CI depends on this too).

## Run locally

```bash
cp .env.example .env                 # adjust if needed (e.g. POSTGRES_HOST_PORT if 5432 is taken)
docker compose up -d postgres        # database
cd services/auth-service
./gradlew bootRun                    # http://localhost:8081 (Swagger: /swagger-ui.html)

# UI (separate terminal)
cd frontend
nvm use && npm install
npm run dev                          # http://localhost:5173 (proxies /api to :8081)
```

Verify: `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`

## Test

```bash
cd services/auth-service
./gradlew test          # integration tests use Testcontainers (needs Docker running)

cd frontend
npm run test:coverage   # Vitest + RTL with coverage gate
npm run test:ui         # Playwright, mocked API
npm run test:e2e        # Playwright, real stack (postgres + bootRun must be up)
```

## Working with Claude

Open this folder in Claude Code (`claude` from the repo root) or Cowork. `CLAUDE.md` gives Claude the project context and conventions.
