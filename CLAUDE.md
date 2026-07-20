# FinTrack — Claude context

Household personal-finance platform: Spring Boot 4 microservices behind a reactive API gateway, a React 19 SPA, and Claude-powered transaction categorization. Owner: ashik (senior engineer, finance industry).

## Read first

- `docs/ARCHITECTURE.md` — services, stack, trade-offs
- `docs/ROADMAP.md` — what's shipped vs. next; **check it before suggesting work**
- `docs/API.md` — endpoint reference (auth + finance)
- `docs/TEMPLATE.md` — the reusable, domain-agnostic auth starter (what to keep/strip when extracting)
- `docs/PRODUCT.md` — the FinTrack finance-tracker domain (households, accounts, budgets, privacy)
- `docs/decisions/` — ADRs. 001 personal-first privacy (product) · 002 JWT keys · 003 refresh cookie · 004 email verification · 005 password reset (002–005 are template-generic) · 006 shared commitments (product) · 007 API gateway · 008 canonical category taxonomy · 009 AI transaction categorization · 010 request correlation IDs.

## Current state

Three services run together via `./dev.sh` (Postgres, Redis, Mailpit, gateway, auth, finance, frontend).

- **auth-service** (:8081): signup + 6-digit email verification, login (RS256 JWT + JWKS), refresh rotation with reuse detection, logout, `/users/me`, password reset with session revocation, authenticated change-password/change-email, login throttling, household **email invitations** (multi-member), request correlation IDs, Swagger. Refresh token = httpOnly `SameSite=Strict` cookie (ADR 003). Email via provider chain (ADR 004): Gmail SMTP / Resend / Mailpit.
- **finance-service** (:8082): accounts, transactions, **CSV import** (dedup, auto-created accounts), **dashboard** (KPIs/category/monthly/merchants), **budgets** + **budget-vs-actual per canonical category** (ADR 008), **AI categorization** (ADR 009, opt-in Claude + rule fallback) with a **recategorize** endpoint, **home loan** + payoff calculator, **income** + **cash flow**/affordability, **shared commitments** (ADR 006, personal-by-default). Verifies auth JWTs via JWKS; correlation IDs (ADR 010).
- **gateway-service** (:8080): reactive Spring Cloud Gateway (ADR 007) — routing, CORS, Redis rate limiting, edge correlation IDs.
- **React 19 SPA**: every flow above — Dashboard, Cash flow, Home loan, Income & expenses, Profile (household roster + invites), Settings; charts (donut/bar/payoff); light/dark.

~250 tests (JUnit + Testcontainers + Karate + Vitest + Playwright), coverage gates enforced. Public repo, branch protection. **Next: see `docs/ROADMAP.md` (Redis refresh-token store, insight-service, containerization → K8s).**

## Stack

Java 25 · Spring Boot 4.1 (Spring Framework 7) · Gradle (Kotlin DSL, shared version catalog `gradle/libs.versions.toml`) · Postgres 17 (schema-per-service) · Redis · Flyway · Testcontainers · Argon2id · Nimbus JOSE (RS256/JWKS) · reactive Spring Cloud Gateway · Anthropic Messages API (Claude, behind a port) · Spring Mail + Resend SDK. **Note: Boot 4.1 uses Jackson 3 (`tools.jackson`), not Jackson 2** — annotations stay `com.fasterxml.jackson.annotation`. Frontend: React 19 · Vite · TypeScript · Tailwind v4 · shadcn-style components · TanStack Query · react-hook-form + zod · Sonner. Ahead: Minikube/GKE, insight-service.

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

## Ways of working

When making non-obvious choices, explain the why briefly. Record significant decisions as ADRs in `docs/decisions/`. Owner types the fix when debugging together — suggest, don't just apply, unless asked.
