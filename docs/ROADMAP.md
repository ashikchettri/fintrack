# FinTrack Roadmap

Each phase produces something working and deployable. Don't start a phase until the previous one's "done when" holds.

## Phase 0 — Setup (½ day)
- Install: Java 25 (SDKMAN), Docker Desktop, Minikube, kubectl, Node 22, gcloud CLI.
- Create monorepo: `services/`, `frontend/`, `infra/`, `docs/` (these two files go in `docs/`), root `CLAUDE.md`.
- GitHub repo + branch protection + empty GitHub Actions workflow.

**Done when:** `docker run hello-world`, `minikube start`, and `java --version` → 25 all work.

## Phase 1 — auth-service, done properly ✅ COMPLETE (+ hardened)
- Spring Boot 4.1 service: signup, login, refresh (rotation + reuse detection), logout, `/users/me`. ✅
- Household groundwork: signup auto-creates a single-member household (`OWNER` role); JWT carries `householdId` + `memberId` + `role` claims. ✅
- Postgres via Docker Compose, Flyway migrations, Argon2id, RS256 JWTs + JWKS endpoint. ✅
- Bean Validation, RFC 9457 Problem Details (+ traceId), springdoc OpenAPI UI. ✅
- Tests: unit + Testcontainers + Karate for every endpoint; CI on every PR. ✅
- **Beyond the original scope** (former stretch items, now built): email verification, password reset, change-password, change-email, login throttling, request correlation IDs, three-provider email (Gmail/Resend/Mailpit). See ADRs 002–005.

**Done ✅:** full signup→verify→login→refresh→logout flow works via Swagger UI and the React UI; token reuse is detected; both Sonar gates green; CI green.

## Phase 2 — finance-service + gateway (1–2 weeks)
- finance-service: accounts/transactions/budgets CRUD, JWT verification via JWKS, ownership checks, pagination/filter/sort.
- All tables scoped by `householdId` + `memberId` from the first migration; transactions carry a `visibility` flag defaulting to `personal` (ADR 001). One member for now — the columns cost nothing, retrofitting costs everything.
- Spring Cloud Gateway: routing, CORS, rate limiting; clients now hit only the gateway. **⏳ scaffolded** — `gateway-service` (reactive, `:8080`) with routing + CORS + Redis rate limiting (ADR 007); frontend cut-over to `:8080` pending end-to-end verification.
- Redis for refresh-token store + login rate limiting. **⏳ in progress** — backs the gateway rate limiter now; refresh-token store next.
- Compose runs the whole stack with one command. ✅ (`./dev.sh`)

**Done when:** you can create/list/delete transactions through the gateway with a JWT from auth-service; a user cannot read another user's data (test proves it).

## Phase 3 — React UI (partly done, pulled forward)
- Vite + React 19 + TypeScript + Tailwind/shadcn. **Auth pages done**: signup, verify-email, login, forgot/reset password, profile, account settings (change password/email); light/dark theme; toasts. ✅
- Auth: access token in memory, refresh via httpOnly cookie; fetch client does one silent refresh on 401. ✅
- TanStack Query for server state; react-hook-form + zod, mirroring backend rules. ✅
- **Remaining** (with Phase 2): dashboard, transactions (add/edit/delete), budgets.

**Auth journey ✅** works in the browser against the local stack (Vitest + Playwright mocked & e2e). Finance pages land alongside finance-service.

## Phase 4 — Claude AI + skills (1 week)
- insight-service with Spring AI + Claude: auto-categorize transactions, monthly summary, NL Q&A via tool use.
- Structured JSON outputs; prompt + eval notes in `docs/ai.md`.
- Build 1–2 custom Claude skills for your dev workflow (endpoint scaffolding, review checklist).

**Done when:** new transactions get auto-categorized and the dashboard shows an AI monthly summary.

## Phase 5 — Docker → Minikube (1–2 weeks)  ← the K8s learning core
- Multi-stage Dockerfiles per service (non-root, layered jars); Trivy scan in CI.
- Raw manifests in `infra/k8s/`: Deployments, Services, Ingress (minikube addon), ConfigMaps, Secrets, liveness/readiness probes, resource limits; Postgres as StatefulSet + PVC.
- Practice ops: `kubectl logs/describe/exec`, rolling updates, rollbacks, scaling, HPA.
- Then convert manifests to a Helm chart.

**Done when:** full app runs on Minikube via `helm install`; you can roll out a new image version with zero downtime and roll it back.

## Phase 6 — Google Cloud (1–2 weeks)
- Artifact Registry for images; GKE Autopilot cluster; Cloud SQL Postgres; Secret Manager; managed cert + HTTPS Load Balancer via Ingress.
- GitHub Actions: on tag → build, scan, push, deploy to GKE (Workload Identity Federation, no key files).
- **Cost guardrails:** billing alerts at $10/$25; tear down with `terraform destroy`/`gcloud` when idle — Autopilot + Cloud SQL idle at roughly $2–4/day.

**Done when:** app is live on a public HTTPS URL, deployed by CI, and you can tear it all down and recreate it from scratch.

## Phase 7 — Family & income (1–2 weeks) — *revisit ADR 001 before starting*
- Framing: **shared budgets and bills**, not expense transparency. Everything defaults `personal`; members deliberately share mortgage/groceries/bills to the household.
- Invitation flow: owner invites partner/child by email — signed, expiring, single-use tokens; accept → join household with `ADULT` or `CHILD` role.
- Income profile per member: salary, super/pension contributions, tax withheld.
- Policy-based authorization: adults see `household` items + aggregate totals only; children see only their own. Tests prove every rule.
- UI: member switcher, family dashboard (shared budgets, bills, per-member totals), income profile page.
- AI extension: family-level monthly summary over shared items; tax estimate as an insight-service feature (clearly labeled as an estimate).

**Done when:** a second real user joins via email invite, both contribute transactions, the family dashboard shows shared budgets + per-member totals, and a test proves an adult cannot read a partner's `personal` transactions.

## Phase 8 — Production polish (ongoing)
- Prometheus + Grafana dashboards; structured JSON logging; distributed tracing (OpenTelemetry).
- Pick extensions by interest: Kafka events between services, Argo CD GitOps, Keycloak swap-in for auth, Terraform for all GCP infra, k6 load testing, NetworkPolicies.

## Rules of the road
1. **Working software every phase** — never more than a few days from something runnable.
2. **Write tests as you go**, not after. Testcontainers from day one.
3. **Commit small, push daily**; CI green before moving on.
4. **Keep a `docs/decisions/` folder** — one short ADR per big choice. Interviewers love this.
5. When stuck >2h, use Claude to debug — but type the fix yourself and note what you learned in `docs/til.md`.
