# FinTrack Roadmap

Each milestone produces something working and deployable. FinTrack is built incrementally — a runnable, tested app at every step.

## Shipped

### Identity & households (auth-service)
- Signup with 6-digit **email verification**, login, refresh (rotation + **reuse detection**), logout, `/users/me`.
- **RS256 JWT + JWKS** — other services verify against the public key, no shared secrets. Claims carry `householdId` + `memberId` + `role`.
- **Argon2id** password hashing; refresh token in an **httpOnly `SameSite=Strict` cookie** (ADR 003).
- **Password reset** (revokes sessions), authenticated **change-password** and **change-email**, login throttling — all one-time codes hashed, TTL'd, attempt-capped (ADR 004/005).
- **Households**: signup creates a single-member household (`OWNER`); the owner **invites** partners/family by email to join and contribute.
- Email via a provider chain: **Gmail SMTP → Resend → Mailpit** (ADR 004).

### Money (finance-service)
- **Accounts & transactions**, every row and query scoped by `householdId` + `memberId`; money as `NUMERIC(19,4)` + currency.
- **CSV import** — bank statement in, deduplicated transactions out, accounts auto-created from the file (the hero flow).
- **Dashboard** — KPIs, spend by category, monthly bars, top merchants, recent activity.
- **Budgets** — household budget (income/expense/savings) with a starter template, rolled up to **budget vs actual** in totals and **per canonical category**.
- **Canonical category taxonomy** (ADR 008) shared by budget lines and transactions.
- **AI categorization** (ADR 009) — Claude behind a `TransactionCategorizer` seam, opt-in, rule-based fallback, plus a **recategorize** action for existing rows.
- **Home loan** + payoff calculator (amortization, interest, extra-repayment savings).
- **Income & cash flow** — per-member income profiles → household income, surplus, affordability.
- **Shared commitments** (ADR 006) — personal-by-default, opt-in sharing, equal-split settlement.

### Platform
- **API gateway** (reactive Spring Cloud Gateway, ADR 007) — single entry point, routing, CORS, Redis-backed rate limiting.
- **Request correlation IDs** (ADR 010) — one `X-Request-Id` across the gateway and services, into logs and every error body.
- **React 19 SPA** — every flow above, light/dark theme, charts, TanStack Query, react-hook-form + zod.
- **CI** on every PR: build, tests, coverage gates, secret scan; branch protection.
- **One-command local stack** (`./dev.sh`): Postgres, Redis, Mailpit, all services, frontend.

## Next

### Redis refresh-token store
- Move refresh tokens off in-process state into Redis (the gateway already uses Redis for rate limiting), so sessions survive restarts and scale horizontally.

### insight-service (AI)
- Monthly spending summaries and natural-language Q&A ("how much did I spend on food in June?") via Claude tool use against finance-service, reusing the Anthropic client pattern from ADR 009. Structured JSON output, batching, cost control.
- Harden categorization: tool-use-enforced structured output and an evaluation set.

### Containerization → Kubernetes
- Multi-stage Dockerfiles per service (non-root, layered jars); Trivy image scan in CI.
- Kubernetes manifests in `infra/k8s/`: Deployments, Services, Ingress, ConfigMaps, Secrets, liveness/readiness probes, resource limits; Postgres as a StatefulSet + PVC. Then a Helm chart.
- Zero-downtime rolling updates and rollbacks.

### Google Cloud
- Artifact Registry, GKE Autopilot, Cloud SQL (Postgres), Secret Manager, managed TLS + HTTPS load balancer via Ingress.
- GitHub Actions on tag → build, scan, push, deploy (Workload Identity Federation, no key files).
- Cost guardrails: billing alerts; tear down when idle.

### Family & richer authorization
- Policy-based authorization: adults see `shared` household items + aggregate totals; children see only their own. Tests prove every rule.
- Family dashboard: shared budgets/bills, per-member totals, member switcher.
- Split rules beyond equal split (income-based, custom weights); a stored `split_agreement`.

### Production hardening
- Prometheus + Grafana, structured JSON logging, **OpenTelemetry** distributed tracing (the correlation id from ADR 010 becomes the trace id).
- Frontend performance (route-level code splitting), surfacing the `traceId` in error UIs.
- Optional: Kafka events between services, Argo CD GitOps, Keycloak swap-in, k6 load testing, NetworkPolicies.

## Principles

1. **Working software at every step** — never far from something runnable.
2. **Tests ship with the feature** — unit + Testcontainers integration + API/e2e; a feature without tests isn't done.
3. **Small PRs, green CI before merge.** Every change goes through a PR against `main`.
4. **One short ADR per significant decision** in `docs/decisions/`.
