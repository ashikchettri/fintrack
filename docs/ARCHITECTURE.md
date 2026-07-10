# FinTrack — Personal Finance Tracker

A learning project covering the modern backend/DevOps stack end-to-end: Spring Boot microservices → Docker → Kubernetes (Minikube) → Google Cloud, with a React UI and Claude AI features.

## 1. Why this project

- **Real domain you know**: accounts, transactions, budgets — you work in finance, so business logic is free.
- **Natural CRUD + auth**: signup/login done "the proper way" first, then add/delete/list features.
- **Family/household model**: multiple members (owner, partner, children) contribute income and expenses under their own profiles, viewable per-member or as a family total. Forces real role-based authorization, not just per-user isolation.
- **AI fits organically**: Claude for transaction categorization and spending insights — not bolted on.
- **Portfolio-grade**: demonstrates the exact stack (Spring Boot, K8s, GCP) employers screen for.

## 2. Tech stack (verified July 2026)

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 25 (LTS)** | Current LTS, supported to 2033; virtual threads mature |
| Framework | **Spring Boot 4.1** (Spring Framework 7) | Latest stable; built-in API versioning, HTTP service clients, modular starters |
| Build | **Gradle (Kotlin DSL)** or Maven | Gradle = faster builds, more market momentum; Maven fine if preferred |
| Database | **PostgreSQL 17** | One instance locally, **schema-per-service** (proper DB-per-service later) |
| Migrations | **Flyway** | Versioned SQL migrations, never `ddl-auto=update` |
| Auth | **Spring Security 7 + JWT** (access + refresh tokens) | Learn it by hand first; note where Keycloak/OAuth2 would replace it |
| API docs | **springdoc-openapi** | Swagger UI per service |
| Cache / tokens | **Redis** (phase 2+) | Refresh-token store, rate limiting |
| Gateway | **Spring Cloud Gateway** | Single entry point, routing, CORS, rate limiting |
| Frontend | **React 19 + Vite + TypeScript** | Current standard; TanStack Query for server state |
| AI | **Claude API via Spring AI** | Spring AI is the idiomatic integration; also build Claude Skills/agents for dev workflow |
| Containers | **Docker** (multi-stage builds, distroless/temurin-jre base) | Small, secure images |
| Local orchestration | **Docker Compose** → **Minikube** | Compose for dev loop, Minikube for K8s learning |
| K8s packaging | Raw manifests first → **Helm** charts | Understand primitives before abstracting |
| Cloud | **GKE Autopilot + Cloud SQL (Postgres) + Artifact Registry + Secret Manager** | The managed-GCP path |
| CI/CD | **GitHub Actions** → build/test/scan/push → deploy | Add **Argo CD** (GitOps) as stretch |
| Observability | Spring Boot Actuator + **Prometheus + Grafana**, structured JSON logs | Standard K8s observability |
| Testing | JUnit 5, **Testcontainers** (real Postgres in tests), MockMvc | Integration tests against real DB |

## 3. Architecture (start modest, evolve)

```
                    ┌─────────────┐
   React SPA  ───▶  │ API Gateway │  (Spring Cloud Gateway)
                    └──────┬──────┘
              ┌────────────┼────────────────┐
              ▼            ▼                ▼
       ┌────────────┐ ┌───────────────┐ ┌────────────────┐
       │ auth-      │ │ finance-      │ │ insight-       │
       │ service    │ │ service       │ │ service (P4)   │
       │ signup/    │ │ accounts,     │ │ Claude AI:     │
       │ login/JWT  │ │ transactions, │ │ categorize,    │
       │ refresh    │ │ budgets CRUD  │ │ insights       │
       └─────┬──────┘ └──────┬────────┘ └───────┬────────┘
             ▼               ▼                  ▼
       ┌──────────────────────────────────────────────┐
       │ PostgreSQL (schema per service)  +  Redis    │
       └──────────────────────────────────────────────┘
```

**Phase 1 scope**: auth-service only, called directly. Gateway and finance-service in phase 2, insight-service in phase 4. Async events (Kafka) deliberately deferred — add later as a learning extension (e.g. "transaction created" → insight-service).

## 4. Service design

### auth-service (the foundation — do this properly)
- `POST /api/v1/auth/signup` — email + password; **Argon2id** hashing (Spring Security default encoder); email-format + password-strength validation (Bean Validation). Signup auto-creates a single-member **household** with role `OWNER`.
- Owns the household model: `households`, `household_members` (role: `OWNER` / `ADULT` / `CHILD`), member **invitations** (signed, expiring, single-use email tokens — build the flow in phase 7).
- JWTs carry `householdId` + `role` claims so downstream services authorize without extra lookups.
- `POST /api/v1/auth/login` — returns short-lived **access JWT (15 min)** + **refresh token (7 days, rotated on use)**.
- `POST /api/v1/auth/refresh` — rotate refresh token; detect reuse (stolen-token defense).
- `POST /api/v1/auth/logout` — revoke refresh token.
- `GET /api/v1/users/me` — authenticated profile.
- JWTs signed **RS256** (asymmetric) so other services verify with the public key (JWKS endpoint) without sharing secrets.
- Rate limiting on login (brute-force defense). Account lockout after N failures.
- Stretch: email verification, password reset, TOTP 2FA, OAuth2 social login.

### finance-service
- CRUD: accounts, transactions (amount, date, merchant, category), budgets — all scoped by `householdId` + `memberId` from day one, even while there's only one member.
- **Income profile per member**: salary, super/pension contributions, tax withheld (store what the payslip says; tax *estimation* is a later insight-service feature since rules change yearly).
- **Transaction visibility flag**: `personal` | `household`, **default `personal`** — sharing is a deliberate opt-in (see ADR 001). Adults see household items + aggregate totals, never each other's personal line items; `CHILD` role sees only their own data. Family view = shared budgets/bills + totals, not full transparency.
- Validates JWT locally via auth-service JWKS. **Policy-based authorization** (role + visibility), not just ownership checks — this is the interesting part.
- Pagination, filtering, sorting on transaction lists — do these properly.
- Money as `NUMERIC(19,4)` + currency code. **Never floating point.**

### insight-service (AI)
- Claude API (via Spring AI) to: auto-categorize transactions, generate monthly spending summaries, answer natural-language questions ("how much did I spend on food in June?") via tool-use against finance-service.
- Learn: prompt design, structured output (JSON schema), tool use, cost control (batching, caching).

## 5. Cross-cutting best practices

- **12-factor config**: everything via env vars / Spring profiles (`local`, `docker`, `k8s`, `gcp`). No secrets in git — `.env` locally, K8s Secrets, then GCP Secret Manager.
- **API versioning** from day one (`/api/v1/...`, Spring Boot 4 native support).
- **Error handling**: RFC 9457 Problem Details (`ProblemDetail` in Spring), consistent error body everywhere.
- **DTOs at the boundary** (Java records), never expose JPA entities. MapStruct optional.
- **Health probes**: Actuator `/actuator/health/liveness` and `/readiness` wired to K8s probes.
- **Docker**: multi-stage build (JDK build stage → JRE runtime stage), non-root user, layered jars, `.dockerignore`. Scan with Trivy in CI.
- **K8s**: resource requests/limits, ConfigMaps + Secrets, HPA, NetworkPolicies as stretch.
- **Git hygiene**: monorepo (`services/auth`, `services/finance`, `frontend/`, `infra/`), conventional commits, PRs even solo, CI on every push.
- **Claude workflow**: keep a `CLAUDE.md` in the repo; build custom **skills** for repetitive tasks (e.g. "scaffold a new endpoint with test + migration"); use Claude for code review before merging.

## 6. Key decisions & trade-offs

| Decision | Alternative | Rationale |
|---|---|---|
| Hand-rolled JWT auth | Keycloak / Auth0 | You learn the mechanics; migrate to Keycloak later as its own lesson |
| Schema-per-service, one Postgres | DB per service | Cheap locally; the isolation concept still holds; split when on GCP |
| Sync REST between services | Kafka events | Fewer moving parts to start; Kafka is a planned phase-5 extension |
| Raw K8s manifests first | Helm from day one | You must understand Deployments/Services/Ingress before templating them |
| Monorepo | Repo per service | One CI setup, atomic changes across services; industry-common for this scale |
| GKE Autopilot | Cloud Run | You explicitly want Kubernetes; Autopilot removes node management only |
| Household model in schema from phase 1, features in phase 7 | Add multi-user later | Retrofitting household scoping touches every table, query, and auth check; carrying an unused `member_id` column is nearly free |
| Store payslip figures (salary, super, tax withheld) | Compute tax | Tax rules change yearly per jurisdiction; estimation becomes an AI insight feature, not core schema |
