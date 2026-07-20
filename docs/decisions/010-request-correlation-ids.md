# ADR 010 — Request correlation IDs across services

**Status:** Accepted · 2026-07-20

## Context

auth-service already stamps a correlation id into its logs (`%X{traceId}`) and every `ProblemDetail`, minting one per request (`CorrelationIdFilter`). But it stopped at the service boundary: finance-service didn't participate, and now that the gateway (ADR 007) is the single entry point, there was no id that spans the hop. A user hitting an error, or us debugging a request that fanned out, had no single id tying the gateway line, the finance line, and the error body together — and that only gets worse in Minikube, where you can't just `tail` one file.

This is cheap to do now and painful to retrofit once there are more services and logs are scattered across pods. It's also the seed of the OpenTelemetry work — the same id becomes the trace id later.

## Decision

One correlation id per request, propagated by an HTTP header, present in every service's logs and error bodies.

1. **Header `X-Request-Id`; MDC key `traceId`.** The gateway is the authoritative source: it honors a safe inbound `X-Request-Id` (from the SPA or another caller), otherwise mints a UUID, then **forwards it downstream and echoes it to the client** — so even a gateway-only response (rate limit, no route) carries a traceable id.

2. **Every service honors the inbound id.** auth- and finance-service run the same `CorrelationIdFilter` (`OncePerRequestFilter`, highest precedence): take the header if it's safe, else mint; put it in MDC; echo it on the response; clear it in a `finally`. So an id set at the edge flows straight through, and a service called directly (no gateway, e.g. a test or `curl :8082`) still gets one.

3. **Logs and error bodies both carry it.** The console log pattern includes `[%X{traceId}]`; the global exception handler copies the MDC value into `ProblemDetail.traceId`. One id links the client's error, the access log, and the service log.

4. **Injection-safe.** The inbound id is only trusted if it matches `[A-Za-z0-9_-]{8,64}` — a hostile header can't smuggle newlines or control chars into the logs/MDC. An unsafe value is discarded and a fresh id minted.

## Consequences

- **Positive:** one grep (`traceId=<id>`) across gateway + service logs reconstructs a request; every error the user sees quotes an id they can hand to support; the convention is uniform across services and ready to become an OTel trace id later. No new dependency — MDC + a filter + a header.
- **Negative / limits:** the gateway is reactive, so its own log lines don't get the id via MDC (thread-bound) without extra Reactor-context plumbing — deferred; the gateway's job here is to **mint + propagate**, and the servlet services do the MDC logging. Propagation rides on the HTTP header, so any future app-level service-to-service call must forward `X-Request-Id` itself (there are none today beyond framework-managed JWKS; the external Anthropic call deliberately does **not** receive it). Not a distributed trace (no spans) — that comes with the OpenTelemetry work.
- **Revisit** with the observability work: replace/augment with OpenTelemetry (W3C `traceparent`), reusing this id as the trace id, and add Reactor-context MDC on the gateway.
