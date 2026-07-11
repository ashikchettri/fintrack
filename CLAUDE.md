# FinTrack — Claude context

Household personal-finance tracker; learning project for Spring Boot 4 microservices → Docker → K8s → GCP. Owner: ashik (senior engineer, finance industry).

## Read first

- `docs/ARCHITECTURE.md` — services, stack, trade-offs
- `docs/ROADMAP.md` — phases; **check which phase we're in before suggesting work**
- `docs/decisions/` — ADRs. ADR 001: transactions default to `personal` visibility; sharing is opt-in.

## Current state

Phase 1 **complete**: auth-service has signup, login (RS256 JWT + JWKS), refresh rotation with reuse detection, logout, /users/me, Swagger UI, login throttling. Refresh token is an httpOnly cookie (ADR 003). React auth UI (phase 3 pulled forward) verifies the API end-to-end — Vitest + Playwright (mocked & e2e). Next: phase 2 finance-service + gateway + Redis.

## Stack

Java 25 · Spring Boot 4.1 · Gradle (Kotlin DSL) · Postgres 17 (Docker Compose) · Flyway · Testcontainers. Later: Spring Cloud Gateway, Redis, React 19, Spring AI + Claude, Minikube/GKE.

## Conventions — enforce these in every change

- API paths: `/api/v1/...`. Errors: RFC 9457 `ProblemDetail`, never ad-hoc error bodies.
- DTOs are Java records at the boundary; never expose JPA entities from controllers.
- Money: `NUMERIC(19,4)` + currency code. Never float/double.
- Schema changes ONLY via new Flyway migrations (`V<n>__description.sql`); never edit an applied migration; never `ddl-auto=update`.
- Every table scoped by `household_id` + `member_id`. Transactions carry `visibility` defaulting to `personal`.
- Passwords: Argon2id. Tokens: RS256 JWTs, 15-min access + rotated refresh; `householdId` + `role` claims.
- Config via env vars / Spring profiles (`local`, `docker`, `k8s`, `gcp`). No secrets in git.
- Tests accompany every feature: unit + Testcontainers integration. A feature without tests is not done.
- Conventional commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`).

## Commands

```bash
docker compose up -d postgres                  # from repo root
cd services/auth-service && ./gradlew bootRun  # run (port 8081)
cd services/auth-service && ./gradlew test     # tests (needs Docker)
```

## Learning mode

This is a learning project: when making non-obvious choices, explain the why briefly. Record significant decisions as ADRs in `docs/decisions/`. Owner types the fix when debugging together — suggest, don't just apply, unless asked.
