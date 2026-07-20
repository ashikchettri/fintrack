# FinTrack — Codex context

Household personal-finance platform: Spring Boot 4 microservices behind a reactive API gateway, a React 19 SPA, and Claude-powered transaction categorization. Owner: ashik (senior engineer, finance industry).

## Read first

- `docs/ARCHITECTURE.md` — services, stack, trade-offs
- `docs/ROADMAP.md` — what's shipped vs. next; **check it before suggesting work**
- `docs/decisions/` — ADRs. ADR 001: transactions default to `personal` visibility; sharing is opt-in.

## Current state

Three services run together via `./dev.sh` (Postgres, Redis, Mailpit, gateway, auth, finance, frontend).

- **auth-service** (:8081): signup + 6-digit email verification (ADR 004), login (RS256 JWT + JWKS), refresh rotation + reuse detection, logout, /users/me, password reset with session revocation (ADR 005), change-password/change-email, throttling, household **email invitations**, correlation IDs, Swagger. Refresh token = httpOnly cookie (ADR 003). Email via provider chain (ADR 004): Gmail / Resend / Mailpit.
- **finance-service** (:8082): accounts, transactions, **CSV import** (dedup), **dashboard**, **budgets** + budget-vs-actual per canonical category (ADR 008), **AI categorization** (ADR 009, opt-in Claude + rule fallback) + recategorize, **home loan** + payoff calculator, **income**/**cash flow**, **shared commitments** (ADR 006). JWKS-verified JWTs; correlation IDs (ADR 010).
- **gateway-service** (:8080): reactive Spring Cloud Gateway (ADR 007) — routing, CORS, Redis rate limiting.
- **React 19 SPA** covers every flow (Vitest + Playwright mocked & e2e); Karate API scenarios.

Public repo with branch protection. Next: see `docs/ROADMAP.md` (Redis refresh-token store, insight-service, containerization → K8s).

## Stack

Java 25 · Spring Boot 4.1 · Gradle (Kotlin DSL) · Postgres 17 · Redis · Flyway · Testcontainers · reactive Spring Cloud Gateway · Anthropic Messages API (Claude) · React 19 + Vite + TS. **Note: Boot 4.1 uses Jackson 3 (`tools.jackson`).** Ahead: Minikube/GKE, insight-service.

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
docker compose up -d postgres                     # from repo root
cd services/auth-service && ./gradlew bootRun     # run (port 8081)
cd services/finance-service && ./gradlew bootRun  # run (port 8082; needs auth-service JWKS)
cd services/<service> && ./gradlew test           # tests (needs Docker)
cd frontend && npm run dev                        # UI (port 5173)
```

## Ways of working

When making non-obvious choices, explain the why briefly. Record significant decisions as ADRs in `docs/decisions/`. Owner types the fix when debugging together — suggest, don't just apply, unless asked.
