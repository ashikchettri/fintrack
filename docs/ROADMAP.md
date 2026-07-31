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
- **Net worth** (ADR 014) — a manual assets/liabilities balance sheet, folding the home loan in for a real net-worth figure.

### Platform
- **API gateway** (reactive Spring Cloud Gateway, ADR 007) — single entry point, routing, CORS, Redis-backed rate limiting.
- **Request correlation IDs** (ADR 010) — one `X-Request-Id` across the gateway and services, into logs and every error body.
- **React 19 SPA** — every flow above, light/dark theme, charts, TanStack Query, react-hook-form + zod.
- **CI** on every PR: build, tests, coverage gates, secret scan; branch protection.
- **One-command local stack** (`./dev.sh`): Postgres, Redis, Mailpit, all services, frontend.

## Next

### Redis refresh-token store — ✅ built (opt-in), default-flip pending
- A `RefreshTokenStore` seam with a Redis implementation (ADR 011): Lua-atomic rotation, single-use, and reuse detection over Redis, with native TTL replacing the purge job. Selected by `fintrack.auth.refresh-token.store=redis`; Postgres (`jpa`) remains the default until the flip is verified in a deployed environment.

### insight-service (AI) — ✅ built
- `insight-service` (`:8083`): **monthly spending summary** (ADR 012) and **natural-language Q&A** (ADR 013) — `POST /api/v1/insights/ask` grounds answers in real data via a Claude **tool-use loop** against finance-service (forwarding the caller's JWT). Both read finance-service service-to-service.
- Next: more Q&A tools (budget adherence, "find transactions like…"); an evaluation set; answer/summary caching; a UI surface.

### Containerization → Kubernetes
- **Done (ADR 015):** multi-stage Dockerfiles for **all four services** — Alpine JRE, layered Boot jar, non-root user (the read-only root filesystem is applied by the k8s `securityContext`, below). Every service plus the frontend is in the CI `image` matrix, gated by a Trivy HIGH/CRITICAL scan (`exit-code: 1`, with time-boxed `.trivyignore` exceptions).
- Kubernetes manifests in `infra/k8s/`: Deployments, Services, Ingress, ConfigMaps, Secrets, liveness/readiness probes, resource limits; Postgres as a StatefulSet + PVC. Then a Helm chart. **Done for Minikube (ADR 016):** Kustomize base with namespace, config/secret split, Postgres StatefulSet+PVC, Redis, Mailpit, **all four service Deployments** (non-root, read-only FS, probes, init-containers), and an **Ingress** fronting the gateway — validated end-to-end on Minikube (external → ingress → gateway → auth returns 401 on bad creds; all services healthy). **Helm chart (ADR 016):** `infra/helm/fintrack` — a parameterized chart (values-driven images, resources, secrets, per-service config; toggle Postgres/Redis/Mailpit for managed alternatives) rendering the same stack, with a `values-gke.example.yaml`. Validated via `helm lint` + a live `helm install` on Minikube (all pods up, ingress→gateway→auth verified). Next: GKE — Cloud SQL, Secret Manager + Workload Identity, managed TLS.
- Zero-downtime rolling updates and rollbacks.

### Google Cloud
- Artifact Registry, GKE Autopilot, Cloud SQL (Postgres), Secret Manager, managed TLS + HTTPS load balancer via Ingress.
- GitHub Actions on tag → build, scan, push, deploy (Workload Identity Federation, no key files).
- Cost guardrails: billing alerts; tear down when idle.
- **Started (ADR 017):** Terraform foundation in `infra/gke/terraform` — Autopilot cluster, Artifact Registry, Cloud SQL 17, Secret Manager, the app's Workload Identity, keyless GitHub WIF, and a reserved ingress IP — `terraform validate`-clean (not yet applied; needs a dedicated project). The Helm chart now carries the **Cloud SQL Auth Proxy sidecar** + Workload Identity SA and a **GCE Ingress with a Google-managed TLS cert** (ManagedCertificate + HTTP→HTTPS FrontendConfig), both off by default so Minikube is unaffected. The **`deploy-gke.yml`** workflow ships it: on a `v*` tag it authenticates via WIF (no keys), builds/Trivy-scans/pushes each image to Artifact Registry, and `helm upgrade`s with `values-gke.yaml`. Remaining before go-live is operational: `terraform apply` against a dedicated project, set the repo variables + k8s secrets, and point DNS at the reserved IP (runbook in `infra/gke/README.md`).

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
