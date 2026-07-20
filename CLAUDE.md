# FinTrack — Claude context

Household personal-finance tracker; learning project for Spring Boot 4 microservices → Docker → K8s → GCP. Owner: ashik (senior engineer, finance industry).

## Read first

- `docs/ARCHITECTURE.md` — services, stack, trade-offs
- `docs/ROADMAP.md` — phases; **check which phase we're in before suggesting work**
- `docs/API.md` — endpoint reference (auth + finance)
- `docs/TEMPLATE.md` — the reusable, domain-agnostic auth starter (what to keep/strip when extracting)
- `docs/PRODUCT.md` — the FinTrack finance-tracker domain (households, accounts, budgets, privacy)
- `docs/decisions/` — ADRs. 001 personal-first privacy (product) · 002 JWT keys · 003 refresh cookie · 004 email verification · 005 password reset (002–005 are template-generic) · 006 shared commitments (product) · 007 API gateway · 008 canonical category taxonomy · 009 AI transaction categorization.

## Current state

Phase 1 **complete + hardened**. auth-service: signup + 6-digit email verification, login (RS256 JWT + JWKS), refresh rotation with reuse detection, logout, `/users/me`, password reset with session revocation, authenticated **change-password** and **change-email**, login throttling, request correlation IDs (traceId in every ProblemDetail), Swagger UI. Refresh token = httpOnly `SameSite=Strict` cookie (ADR 003). Email via a provider chain (ADR 004): Gmail SMTP / Resend / Mailpit — `./dev.sh` picks Gmail if configured else Mailpit; `./dev.sh mailpit|resend` flip it. React UI (Vite + TS + Tailwind + shadcn + TanStack Query + react-hook-form + zod + Sonner + light/dark theme) covers every auth flow. finance-service scaffolded, verifying auth JWTs via JWKS. ~200 tests (JUnit + Testcontainers + Karate + Vitest + Playwright), both Sonar gates > 90%. Repo public with branch protection (7 required checks). Local stack: `./dev.sh`. **Next: phase 2 finance-service accounts/transactions/budgets.**

## Stack

Java 25 · Spring Boot 4.1 · Gradle (Kotlin DSL, shared version catalog `gradle/libs.versions.toml`) · Postgres 17 · Flyway · Testcontainers · Argon2id · Nimbus JOSE (RS256/JWKS) · Spring Mail + Resend SDK. Frontend: React 19 · Vite · TypeScript · Tailwind v4 · shadcn-style components · TanStack Query · react-hook-form + zod · Sonner. Later: Spring Cloud Gateway, Redis, Spring AI + Claude, Minikube/GKE.

## Conventions — enforce these in every change

- API paths: `/api/v1/...`. Errors: RFC 9457 `ProblemDetail`, never ad-hoc error bodies.
- DTOs are Java records at the boundary; never expose JPA entities from controllers.
- Money: `NUMERIC(19,4)` + currency code. Never float/double.
- Schema changes ONLY via new Flyway migrations (`V<n>__description.sql`); never edit an applied migration; never `ddl-auto=update`.
- Every table scoped by `household_id` + `member_id`. Transactions carry `visibility` defaulting to `personal`.
- Passwords: Argon2id. Tokens: RS256 JWTs, 15-min access + rotated refresh; `householdId` + `role` claims.
- Config via env vars / Spring profiles (`local`, `docker`, `k8s`, `gcp`). No secrets in git.
- Tests accompany every feature: unit + Testcontainers integration + Karate (backend), Vitest + Playwright mocked & e2e (frontend). A feature without tests is not done.
- Frontend forms use react-hook-form + zod; transient errors → Sonner toast, field/detail errors → inline.
- Conventional commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`). **Every change goes through a PR against `main`; never commit to main directly.**
- **No stacked PRs** — base every PR on `main` (stacked PRs got merged out of order twice, stranding commits). If work depends on an unmerged PR, fold it into that PR's branch or wait for the merge.
- One-time-code flows (verification, reset, email-change) share the same hardening: SHA-256 hash at rest, 15-min TTL, 5-attempt cap, no enumeration. The attempt counter survives the 4xx via `noRollbackFor`.

## Commands

```bash
./dev.sh                    # from repo root: check + start Postgres, Mailpit, auth-service, frontend
./dev.sh status | stop      # inspect / tear down (data kept)
./dev.sh mailpit | resend   # switch the backend email transport
cd services/<service> && ./gradlew build   # unit + integration + Karate + coverage gate (needs Docker)
cd frontend && npm run test:coverage       # Vitest; npm run test:ui (mocked) / test:e2e (real, after ./dev.sh mailpit)
```

Machine-specific gotchas (also in Claude memory): a native Postgres owns 5432, so compose maps `POSTGRES_HOST_PORT=5433` and `bootRun` needs `DB_PORT=5433` — `./dev.sh` handles both. `DOCKER_DEFAULT_PLATFORM=linux/amd64` in the shell forces amd64 images on this arm64 Mac; `./dev.sh` overrides to arm64.

## Learning mode

This is a learning project: when making non-obvious choices, explain the why briefly. Record significant decisions as ADRs in `docs/decisions/`. Owner types the fix when debugging together — suggest, don't just apply, unless asked.
