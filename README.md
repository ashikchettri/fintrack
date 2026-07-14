# FinTrack

Household personal-finance tracker. Learning project covering Spring Boot 4 microservices, Postgres, Docker, Kubernetes (Minikube → GKE), React, and Claude AI.

Docs: [architecture](docs/ARCHITECTURE.md) · [roadmap](docs/ROADMAP.md) · [API reference](docs/API.md) · [decisions](docs/decisions/)

Two lenses on this repo: **[TEMPLATE.md](docs/TEMPLATE.md)** — the reusable, domain-agnostic auth starter (API + UI) you can lift into any project · **[PRODUCT.md](docs/PRODUCT.md)** — the FinTrack finance-tracker product (households, accounts, budgets, privacy model).

## Status

**Phase 1 complete + hardened.** A production-grade authentication platform (two services + React UI) is done and tested end-to-end. Phase 2 (finance-service accounts/transactions/budgets) is next.

What works today:

- **Signup** with email verification (6-digit code) · **login** (RS256 JWT + JWKS) · silent **refresh** with rotation + reuse detection · **logout**
- **Password reset** and authenticated **change-password** / **change-email**, all with emailed codes
- Argon2id hashing · httpOnly `SameSite=Strict` refresh cookie · login throttling · request correlation IDs
- **finance-service** scaffolded, verifying auth-service JWTs via JWKS (no shared secrets)
- React UI (Vite + TS + Tailwind + shadcn) covering every auth flow, light/dark themed
- ~200 tests (JUnit + Testcontainers + Karate + Vitest + Playwright), both Sonar gates > 90%, CI on every PR

## Layout

```
services/auth-service/     Spring Boot 4.1 · Java 25 — identity, JWT, households, email (port 8081)
services/finance-service/  Spring Boot 4.1 · Java 25 — accounts, transactions, budgets (port 8082)
frontend/                  React 19 + Vite + TS + Tailwind/shadcn (port 5173)
infra/                     Docker Compose now, K8s manifests + Helm later
docs/                      Architecture, roadmap, API reference, ADRs
gradle/libs.versions.toml  Shared version catalog for both services
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

**One command starts everything** — Postgres, Mailpit, auth-service, and the frontend:

```bash
cp .env.example .env        # first time only
./dev.sh                    # start whatever isn't running (idempotent)
./dev.sh status             # what's up + which email transport is active
./dev.sh stop               # stop apps, pause containers (data kept)
./dev.sh mailpit            # switch backend to the local mail sink (for e2e)
./dev.sh resend             # switch backend to Resend
```

Then open:

| URL | What |
|---|---|
| http://localhost:5173 | the app |
| http://localhost:8081/swagger-ui.html | auth-service API (Swagger) |
| http://localhost:8025 | Mailpit inbox (local email lands here) |

App logs go to `.dev-logs/`. `./dev.sh` sends **real email** via Gmail when `MAIL_USERNAME`/`MAIL_PASSWORD` are set in `.env`, otherwise the local Mailpit inbox — see [Email](#email).

## Test

```bash
# backend (each service; needs Docker for Testcontainers)
cd services/auth-service && ./gradlew build     # unit + integration + Karate + coverage gate
cd services/finance-service && ./gradlew build

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

## Working with Claude

Open this folder in Claude Code (`claude` from the repo root) or Cowork. `CLAUDE.md` gives Claude the project context and conventions.
