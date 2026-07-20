# ADR 007 — API gateway (single entry point)

**Status:** Accepted · 2026-07-20

## Context

The roadmap calls for an API gateway, and we've now hit the reasons for it. Today the browser reaches two services directly and the split lives in the **Vite dev proxy** (`/api/v1/accounts`, `/api/v1/household/**`, … → finance `:8082`; everything else → auth `:8081`). That works for local dev but leaves real gaps:

- **No single entry point.** In prod there is nothing to put one Ingress / one TLS cert / one public hostname in front of. The routing knowledge only exists in a dev-only config that never ships.
- **Cross-cutting concerns are per-service or absent.** CORS is configured (or will be) in each service; **rate limiting does not exist anywhere** — login and signup have no edge throttle beyond auth-service's own in-process login counter, and a client can hammer any endpoint.
- **It's the keystone for the rest of the roadmap.** Ingress, GKE, one HTTPS URL, and a coherent rate-limit story all assume a gateway. Building it now unblocks the Kubernetes and cloud-deploy work.

What we are **not** trying to solve here: the gateway is not where authorization moves to (see Decision 3), and it is not a place for business logic. It routes, applies CORS, and rate-limits. Nothing else.

## Decision

1. **Add `gateway-service` as the only public port (`:8080`).** auth (`:8081`) and finance (`:8082`) become internal — in dev they still bind localhost, in K8s they become `ClusterIP` services with no Ingress. The frontend (Vite proxy in dev, Ingress in prod) targets **only** `:8080`.

2. **Reactive Spring Cloud Gateway**, not the servlet (MVC) gateway. The business services stay Spring MVC; the gateway is a thin routing layer where the reactive model is contained and never leaks into domain code. We pick reactive because its **Redis-backed rate limiter** (`RedisRateLimiter` + `KeyResolver`) is the mature, best-documented path.
   - *Rejected — Spring Cloud Gateway Server MVC:* same servlet model as the rest of the app (tempting), but its rate-limiting story is newer and thinner. The one-programming-model benefit doesn't reach the business services anyway, since the gateway shares no code with them.

3. **Authorization stays at the services — the gateway does not authenticate.** finance-service and auth-service already validate every JWT against auth-service's JWKS (RS256, no shared secret). The gateway **forwards the bearer token untouched** and lets the services remain the single authorization authority.
   - *Rejected — terminate auth at the edge and forward identity via trusted headers:* this makes every downstream service trust the internal network, so any foothold inside the cluster = full data access. Zero-trust between services is worth far more than the saved token-parse.
   - *Rejected (for now) — validate the JWT at the edge as well (defense in depth):* attractive, but it forces the gateway to duplicate auth-service's public-endpoint allowlist (login, signup, verify, forgot/reset, refresh, JWKS, join are all token-less). That map drifts. We keep one authority (the services) and revisit edge validation once the public-route set is stable. Recorded as future hardening, not v1.

4. **CORS moves to the gateway.** It's the only browser-facing origin, so it owns the single CORS policy: allow the SPA origin, `allowCredentials: true` (the refresh token is an httpOnly cookie — ADR 003 — so credentialed requests must be allowed, which forbids a `*` origin). Downstream services stop needing CORS config.

5. **Rate limiting is Redis-backed (token bucket).** This is the first Redis use (the refresh-token store is planned). v1 keys on **client IP** (honours `X-Forwarded-For` for when a load balancer sits in front), which protects the token-less brute-force surface — login and signup — and abusive clients generally. **Per-user keying by JWT subject** is the next slice, and lands together with edge JWT decoding (Decision 3's future item). Buckets: 20 req/s steady, burst 40, tunable per route.

6. **Routes mirror today's proxy split**, so nothing about the API surface changes:
   - `/api/v1/accounts/**`, `/api/v1/transactions/**`, `/api/v1/imports/**`, `/api/v1/dashboard/**`, `/api/v1/household/**` (singular) → **finance-service**
   - everything else under `/api/**` → **auth-service**
   - The singular `/household/**` (finance) vs plural `/households/**` (auth) distinction is load-bearing and preserved: the finance predicate's literal `household` segment never matches `households`, so plural falls through to the auth catch-all — exactly as the Vite proxy does today.
   - Routes are defined with the `RouteLocatorBuilder` **fluent API in Java**, not YAML: it's compile-checked and immune to the gateway's shifting config-property paths across release trains.

## Consequences

- **Positive:** one public URL, one CORS policy, one rate-limit choke point; the routing knowledge now ships as code instead of living in a dev-only proxy. Services keep enforcing their own authz, so the security model doesn't weaken. Redis earns its keep for both rate limiting and (next) the refresh-token store. The Ingress/GKE story now has an obvious front door.
- **Negative / cost:** a third service to run locally (`./dev.sh` now starts it) and a new dependency on Redis for the rate limiter. The gateway adds one network hop. Two Spring stacks now live in the repo (reactive here, MVC everywhere else) — acceptable because they share no code.
- **Cut-over is deliberate, not automatic.** This slice scaffolds the gateway and stands it up on `:8080`; the frontend keeps its per-service proxy until the gateway is verified end-to-end, then the Vite proxy collapses to a single `:8080` target (and, in prod, the Ingress points only at the gateway). Until then the gateway runs in parallel and can be exercised directly.
- **Version pin (verified):** Spring Cloud's current train (`2025.1.0`) supports Spring Boot **4.0.x, not 4.1.0** — Spring Cloud always trails Boot, and its compatibility verifier hard-fails the mismatch at startup. So the gateway module pins Spring Boot to **4.0.0** (in its own `build.gradle.kts`, not the shared `4.1.0` catalog), while auth/finance stay on 4.1.0. This is safe because the gateway shares no code with them. Revisit when a Boot 4.1-compatible Cloud train ships, then align to the catalog. The reactive starter is `spring-cloud-starter-gateway-server-webflux`; the fluent Java route API and the `RedisRateLimiter`/`KeyResolver`/`CorsWebFilter` types are stable across trains. `./gradlew build` (5 tests incl. a Testcontainers-Redis context load + 90% coverage gate) is green.
- **Follow-ups:** edge JWT validation + per-user rate-limit keys ([[revisit]]); aggregate the two services' Swagger under the gateway; a `gateway-service` CI job is added to the matrix, and adding it as a **required** status check on `main` is the owner's one-time branch-protection step.
