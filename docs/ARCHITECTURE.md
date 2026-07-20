# FinTrack — Architecture

FinTrack is a household personal-finance platform built as Spring Boot microservices behind a reactive API gateway, with a React single-page app and Claude-powered transaction categorization. This document covers the services, the stack, and the decisions behind them.

## 1. Why FinTrack

- **A whole-household view.** Banks show one customer one account. FinTrack imports everyone's statements, budgets, income, and loans into one picture the household can act on together.
- **Privacy by construction.** The defensible difference from a bank app: coordinate *shared* money (rent, groceries, bills) without exposing *personal* money. Transactions are personal by default and shared only by deliberate opt-in (ADR 001, ADR 006).
- **Instant, correct categorization.** The hero flow is "upload a CSV → instant dashboard." Claude turns raw bank rows into a canonical category taxonomy so budget-vs-actual is meaningful per category, not just in totals.
- **Real financial modelling.** Home-loan payoff, cash flow, affordability, and an equal-split settlement across household members.

## 2. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 25 (LTS)** | Current LTS; mature virtual threads |
| Framework | **Spring Boot 4.1** (Spring Framework 7) | Native API versioning, HTTP service clients, modular starters |
| Build | **Gradle (Kotlin DSL)** | Fast incremental builds; a shared version catalog pins every service |
| Database | **PostgreSQL 17** | One instance, **schema-per-service** (`auth`, `finance`); splits cleanly to DB-per-service later |
| Migrations | **Flyway** | Versioned, append-only SQL; never `ddl-auto=update` |
| Auth | **Spring Security + JWT** | RS256 access tokens + JWKS; no shared secrets between services |
| Gateway | **Spring Cloud Gateway** (reactive) | Single entry point: routing, CORS, Redis-backed rate limiting (ADR 007) |
| Cache / limits | **Redis** | Gateway rate-limit token buckets (refresh-token store planned) |
| API docs | **springdoc-openapi** | Swagger UI per service |
| Frontend | **React 19 + Vite + TypeScript** | TanStack Query for server state; Tailwind v4 + shadcn-style components |
| AI | **Claude** (Anthropic Messages API) | Transaction categorization behind a pluggable port (Spring AI drops in when it supports Boot 4.1 — ADR 009) |
| Containers | **Docker** (multi-stage, non-root, layered jars) | Small, secure images |
| Orchestration | **Docker Compose** now → **Kubernetes** (Minikube → GKE) | Compose for the dev loop; K8s for deployment |
| Cloud | **GKE Autopilot + Cloud SQL + Artifact Registry + Secret Manager** | The managed-GCP path |
| CI/CD | **GitHub Actions** → build/test/coverage/secret-scan → deploy | Runs on every PR; branch protection |
| Observability | Actuator health probes · request correlation IDs (ADR 010) · Prometheus/Grafana + OpenTelemetry planned | |
| Testing | JUnit 5, **Testcontainers**, Karate, Vitest + RTL, Playwright | Integration tests against real Postgres/Redis |

## 3. Architecture

```
                     React SPA (Vite)
                            │
                  ┌─────────▼──────────┐
                  │   gateway-service  │  reactive Spring Cloud Gateway (:8080)
                  │  routing · CORS ·  │  single public entry point
                  │  rate limiting     │
                  └───────┬───────┬────┘
              ┌───────────┘       └───────────┐
      ┌───────▼───────┐              ┌─────────▼────────┐        ┌────────────────┐
      │ auth-service  │    JWKS      │ finance-service  │        │ insight-service│
      │ signup/login/ │◀────────────▶│ accounts, txns,  │        │ (planned):     │
      │ JWT/refresh,  │   verify     │ import, budgets, │        │ summaries,     │
      │ households,   │              │ income, home     │        │ NL Q&A         │
      │ email (:8081) │              │ loan, AI cats    │        │                │
      └───────┬───────┘              │ (:8082)          │        └────────────────┘
              │                      └────────┬─────────┘
              └───────────┬───────────────────┘
          ┌───────────────▼────────────────┐
          │  PostgreSQL (schema per svc)   │      Redis (rate limiting)
          └────────────────────────────────┘
```

**Built today:** the gateway, auth-service, finance-service, Redis, Postgres, and the React SPA all run together (`./dev.sh`). `insight-service` (AI summaries + natural-language Q&A) is the next planned service; AI *categorization* was pulled forward into finance-service (ADR 009). Async events (Kafka) between services are deliberately deferred. Concrete endpoints: [`docs/API.md`](API.md); design decisions: [`docs/decisions/`](decisions/).

## 4. Service design

### gateway-service (the single entry point) — reactive Spring Cloud Gateway
- The only public port (`:8080`); auth- and finance-service are internal.
- Routes `/api/v1/**` to the right service (the singular `/household/**` finance paths vs plural `/households/**` auth paths are kept distinct), applies one CORS policy (credentialed for the refresh cookie), and rate-limits with a Redis token bucket keyed on client IP.
- Mints the `X-Request-Id` correlation id at the edge and forwards it downstream (ADR 007, ADR 010). Authorization stays at the services — the gateway forwards the bearer token untouched; it never becomes an authorization authority.

### auth-service (identity)
- `POST /auth/signup` — email + password; **Argon2id** hashing; Bean Validation (12–128 char password). Auto-creates a single-member **household** with role `OWNER`, then emails a 6-digit verification code.
- Owns the household model: `households`, `household_members` (role `OWNER` / `ADULT` / `CHILD`), and **email invitations** — the owner invites a partner/family member, who joins the household on accepting.
- JWTs carry `householdId` + `memberId` + `role` claims so downstream services authorize without extra lookups; signed **RS256**, verified via `/.well-known/jwks.json` (no shared secrets).
- `POST /auth/login` — access JWT (15 min, in body) + refresh token (7 days, rotated) as an **httpOnly `SameSite=Strict` cookie** (ADR 003). Login requires a verified email; throttled 5 fails / 15 min.
- `POST /auth/refresh` — rotate + **reuse detection** (a replayed rotated token revokes every session). `POST /auth/logout` — revoke + clear the cookie.
- **Email verification**, **password reset** (revokes sessions), authenticated **change-password** and **change-email** — all with hashed, TTL'd, attempt-capped codes (ADR 004/005).
- `GET /users/me` — authenticated profile. Request **correlation IDs** (`X-Request-Id` → logs + every ProblemDetail `traceId`, ADR 010).
- Email transport is a provider chain: **Gmail SMTP → Resend → Mailpit** (ADR 004).

### finance-service (the money)
- **Accounts & transactions** — scoped by `householdId` + `memberId` on every row and query. Money is `NUMERIC(19,4)` + a currency code, never floating point.
- **CSV import** — the hero flow: a bank statement in, transactions out. The statement's own account column becomes real accounts; rows are deduplicated (SHA-256 natural-key digest) so re-imports never double-count.
- **Dashboard** — KPIs, spend by category, monthly income/expense bars, top merchants, recent activity.
- **Budgets** — a household budget (income / expense / savings lines by frequency), with a starter template; rolled up to **budget vs actual**, in totals and per canonical category.
- **Canonical category taxonomy** (ADR 008) — one `SpendingCategory` vocabulary both budget lines and transactions map to, so the two sides can be compared.
- **AI categorization** (ADR 009) — Claude classifies transactions into that taxonomy behind a `TransactionCategorizer` seam; opt-in, with a deterministic rule-based fallback and a **recategorize** action for existing rows.
- **Home loan** — mortgage details + a payoff calculator (amortization, total interest, extra-repayment savings).
- **Income & cash flow** — per-member income profiles roll up to household income, surplus, and affordability.
- **Shared commitments** (ADR 006) — transactions are `personal` by default; a member opts specific rows into a `shared` household view. The household sees only shared items and an equal-split settlement; personal rows are structurally unreachable in the shared query.
- Validates JWTs locally via auth-service JWKS; honors the `X-Request-Id` correlation id; RFC 9457 Problem Details with `traceId`.

### insight-service (AI) — planned
- Claude via the same Anthropic client pattern as finance-service: monthly spending summaries and natural-language questions ("how much did I spend on food in June?") via tool use against finance-service. Structured JSON output, batching, cost control.

## 5. Cross-cutting practices

- **12-factor config**: everything via env vars / Spring profiles (`local`, `docker`, `k8s`, `gcp`). No secrets in git — `.env` locally, K8s Secrets, then GCP Secret Manager.
- **API versioning** from day one (`/api/v1/...`).
- **Error handling**: RFC 9457 Problem Details everywhere, with a `traceId` correlation id in every error body.
- **DTOs at the boundary** (Java records), never exposing JPA entities.
- **Multi-tenant isolation**: every finance query filters by `householdId` (+ `memberId` for member-owned data) drawn from the verified JWT — never from request input. Tests prove a member can't read another's data.
- **Health probes**: Actuator `/actuator/health/liveness` and `/readiness`, wired to K8s probes.
- **Containers**: multi-stage build (JDK build → JRE runtime), non-root user, layered jars, Trivy scan in CI (planned).
- **Git hygiene**: monorepo, conventional commits, PRs for every change, CI green before merge.

## 6. Key decisions & trade-offs

| Decision | Alternative | Rationale |
|---|---|---|
| RS256 JWT + JWKS, no shared secrets | Symmetric HS256 / Keycloak | Services verify tokens independently against a public key; no secret to leak or rotate across services |
| Schema-per-service, one Postgres | DB per service | Simple to operate; the isolation boundary still holds and splits cleanly later |
| Reactive gateway, MVC services | MVC gateway everywhere | The reactive model stays contained in the routing layer; its Redis rate-limiter is the mature path (ADR 007) |
| Anthropic API behind a port | Spring AI directly | Spring AI trails Boot 4.1; the port keeps callers unchanged and lets Spring AI drop in later (ADR 009) |
| Sync REST between services | Kafka events | Fewer moving parts; event-driven flows are a planned extension |
| Household scoping in the schema from day one | Add multi-user later | Retrofitting household scoping touches every table, query, and auth check; carrying the columns early is nearly free |
| Store payslip figures (salary, super, tax withheld) | Compute tax | Tax rules change yearly per jurisdiction; estimation belongs in insight-service, not core schema |
| Personal-by-default visibility, opt-in sharing | Full household transparency | Full transparency makes people stop logging; opt-in sharing preserves honest data (ADR 001/006) |
