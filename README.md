# FinTrack

A household personal-finance platform: import your bank statements, see your whole financial position on one dashboard, plan a budget, and coordinate shared costs with your household — without giving up your financial privacy.

Built as a Spring Boot 4 microservice backend (Java 25), a reactive API gateway, and a React 19 single-page app, with Claude-powered transaction categorization.

Docs: [architecture](docs/ARCHITECTURE.md) · [roadmap](docs/ROADMAP.md) · [API reference](docs/API.md) · [product](docs/PRODUCT.md) · [decisions](docs/decisions/)

## What FinTrack does

- **CSV → dashboard.** Upload a bank statement and get an instant dashboard — accounts are created from the file, rows are deduplicated on re-import, and spending is summarized by category, month, and merchant.
- **Budget vs actual.** Build a household budget (income, expenses, savings) and see planned vs actual spending — in totals and **per category**, since transactions and budget lines share one canonical category vocabulary.
- **AI categorization.** Claude classifies each transaction into the canonical categories (opt-in); a one-click **Recategorize** re-runs it over existing history. A deterministic rule-based mapper is the always-on fallback.
- **Home loan & payoff calculator.** Record your mortgage and model payoff time, total interest, and how extra repayments shrink both — with an amortization chart.
- **Cash flow & affordability.** Per-member income profiles roll up into household income, surplus, and what you can afford.
- **Shared commitments, privacy-first.** Every transaction is **personal by default**; a member opts specific rows into a household "shared" view (rent, groceries, bills). The household sees only shared items and an equal-split settlement — never anyone's personal spending.
- **Households.** A signup creates a single-member household (`OWNER`); the owner invites partners/family by email to join and contribute.
- **Full account lifecycle.** Signup with email verification, login, silent token refresh, logout, password reset, and authenticated change-password / change-email.

## Architecture at a glance

```
                     React SPA (Vite, :5173)
                              │
                    ┌─────────▼──────────┐
                    │  gateway-service   │  reactive Spring Cloud Gateway (:8080)
                    │  routing · CORS ·  │  single entry point
                    │  rate limiting     │
                    └───────┬───────┬────┘
                  ┌─────────┘       └─────────┐
          ┌───────▼───────┐          ┌────────▼────────┐
          │ auth-service  │  JWKS    │ finance-service │
          │ identity,JWT, │◀────────▶│ accounts, txns, │
          │ households,   │  verify  │ budgets, import,│
          │ email (:8081) │          │ AI cats (:8082) │
          └───────┬───────┘          └────────┬────────┘
                  └──────────┬────────────────┘
              ┌──────────────▼───────────────┐
              │ PostgreSQL (schema-per-svc)  │   Redis (rate limiting)
              └──────────────────────────────┘
```

Services are stateless and JWT-secured: auth-service signs **RS256** access tokens and publishes a JWKS endpoint; finance-service and the gateway verify against it, so there are **no shared secrets**. Every request carries an `X-Request-Id` correlation id that flows across services into logs and error bodies.

## Technology

| Layer | Stack |
|---|---|
| Language / runtime | **Java 25**, **Spring Boot 4.1** (Spring Framework 7) |
| Services | auth-service, finance-service (Spring MVC) · gateway-service (reactive Spring Cloud Gateway) |
| Data | **PostgreSQL 17** (schema per service) · **Flyway** migrations · **Redis** (gateway rate limiting) |
| Security | Spring Security · **Argon2id** password hashing · **RS256** JWT + JWKS · httpOnly `SameSite=Strict` refresh cookie |
| AI | **Claude** (Anthropic Messages API) for transaction categorization, behind a pluggable port |
| API | RFC 9457 Problem Details · `/api/v1/...` versioning · **springdoc-openapi** (Swagger UI per service) |
| Frontend | **React 19**, **Vite**, **TypeScript**, **Tailwind CSS v4**, shadcn-style components, **TanStack Query**, react-hook-form + zod, Sonner |
| Build | **Gradle** (Kotlin DSL, shared version catalog) · **npm** |
| Testing | JUnit 5, **Testcontainers** (real Postgres/Redis), Karate (API), **Vitest** + React Testing Library, **Playwright** (mocked + e2e) |
| CI/CD | **GitHub Actions** — build, test, coverage gates, secret scan on every PR; branch protection |
| Email | Provider chain: **Gmail SMTP → Resend → Mailpit** |

## Layout

```
services/auth-service/     Spring Boot — identity, JWT, households, email (port 8081)
services/finance-service/  Spring Boot — accounts, transactions, budgets, import, AI categorization (port 8082)
services/gateway-service/  reactive Spring Cloud Gateway — routing, CORS, rate limiting (port 8080)
frontend/                  React 19 + Vite + TS + Tailwind/shadcn (port 5173)
infra/                     Docker Compose now; Kubernetes manifests + Helm next
docs/                      Architecture, roadmap, API reference, product, ADRs
gradle/libs.versions.toml  Shared version catalog for the services
dev.sh                     One-command local stack (check + start everything)
```

## Prerequisites (one-time)

```bash
# SDKMAN manages Java/Gradle versions
curl -s "https://get.sdkman.io" | bash
sdk install java 25-tem
sdk install gradle

# Node 22 via nvm (frontend); .nvmrc pins it
nvm install 22

# Docker Desktop must be installed and running
```

## Run locally

**One command starts everything** — Postgres, Redis, Mailpit, the services, and the frontend:

```bash
cp .env.example .env        # first time only
./dev.sh                    # start whatever isn't running (idempotent)
./dev.sh status             # what's up + which email transport is active
./dev.sh stop               # stop apps, pause containers (data kept)
./dev.sh gateway            # (re)start just the gateway
./dev.sh mailpit            # switch backend to the local mail sink (for e2e)
./dev.sh resend             # switch backend to Resend
```

Then open:

| URL | What |
|---|---|
| http://localhost:5173 | the app |
| http://localhost:8080 | API gateway (single entry point) |
| http://localhost:8081/swagger-ui.html | auth-service API (Swagger) |
| http://localhost:8082/swagger-ui.html | finance-service API (Swagger) |
| http://localhost:8025 | Mailpit inbox (local email lands here) |

App logs go to `.dev-logs/`. `./dev.sh` sends **real email** via Gmail when `MAIL_USERNAME`/`MAIL_PASSWORD` are set in `.env`, otherwise the local Mailpit inbox — see [Email](#email).

Inspect the database with `./db.sh` (`./db.sh tables`, `./db.sh user <email>`, `./db.sh query "…"`) — see [docs/DATABASE.md](docs/DATABASE.md).

### AI categorization (optional)

Categorization runs on a deterministic rule-based mapper by default. To use Claude, set in `.env`:

```bash
FINANCE_AI_CATEGORIZATION_ENABLED=true
ANTHROPIC_API_KEY=sk-ant-...
```

Only a transaction's description, the bank's category label, and the debit/credit sign are sent to the model — never amounts, account numbers, balances, or identity ([ADR 009](docs/decisions/009-ai-transaction-categorization.md)).

## Test

```bash
# backend (each service; needs Docker for Testcontainers)
cd services/auth-service && ./gradlew build      # unit + integration + Karate + coverage gate
cd services/finance-service && ./gradlew build
cd services/gateway-service && ./gradlew build

# frontend
cd frontend
npm run test:coverage   # Vitest + RTL with coverage gate
npm run test:ui         # Playwright, mocked API (no backend)
./dev.sh mailpit        # (from repo root) then:
npm run test:e2e        # Playwright, real stack — reads codes from Mailpit
```

## Email

Verification / reset / change-email codes are sent through a provider chain (see [ADR 004](docs/decisions/004-email-verification.md)):

1. **Gmail SMTP** when `MAIL_USERNAME` + `MAIL_PASSWORD` (a Google *App Password*) are set — reaches any recipient.
2. **Resend** when `RESEND_API_KEY` is set — needs a verified domain to reach arbitrary addresses.
3. **Mailpit** otherwise — the local dev sink at http://localhost:8025.

`./dev.sh mailpit` / `./dev.sh resend` flip the transport; a plain `./dev.sh` restores the default (Gmail if configured, else Mailpit). No secrets in git — everything is `.env` / env vars.

## Two lenses on this repo

- **[docs/PRODUCT.md](docs/PRODUCT.md)** — the FinTrack finance product (households, accounts, budgets, the privacy model).
- **[docs/TEMPLATE.md](docs/TEMPLATE.md)** — the reusable, domain-agnostic authentication starter (API + UI) that can be lifted into any project.
