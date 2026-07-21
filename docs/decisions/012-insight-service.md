# ADR 012 — insight-service (AI spending insights)

**Status:** Accepted · 2026-07-21

## Context

AI categorization (ADR 009) turns raw bank rows into a canonical taxonomy, but the *reading* half of the AI story — "what does my spending actually say?" — has no home. Categorization is a transaction write-path concern and rightly lives in finance-service. Monthly narratives and natural-language questions are different: they're read-only, cross-cutting, potentially expensive, and don't belong on the transaction hot path. They want their own service.

## Decision

1. **A new `insight-service`** (Spring Boot 4.1, `:8083`), behind the gateway, that produces AI spending insights over a household's finance data. v1 ships the **monthly spending summary**: `GET /api/v1/insights/monthly-summary` → a short headline + a few insight bullets for the caller's latest (or selected) month. Natural-language Q&A via Claude tool use is the next slice, not this one.

2. **It reads finance data through finance-service's API, not its database.** insight-service holds no finance schema; on a request it calls finance-service's `/api/v1/dashboard` **service-to-service**, forwarding the caller's bearer token (so finance-service's own household/member scoping applies — insight-service never sees data the caller couldn't). This is the first app-level service-to-service call, so it also forwards the `X-Request-Id` correlation id (ADR 010).

3. **A `SummaryGenerator` seam with a deterministic fallback**, mirroring the categorizer (ADR 009):
   - `TemplateSummaryGenerator` (default) composes the summary from the numbers — "In June you spent $4,120 across 78 transactions; groceries led at $1,240 (30%)." Works with **no API key**, so the service (and CI) run keyless.
   - `ClaudeSummaryGenerator` (`@Primary` when `insight.ai.enabled=true`) sends the figures to Claude for a natural narrative + insights, and **falls back to the template** on any error/malformed response.
   The model call sits behind the same `AnthropicChatClient` port + RestClient implementation as finance-service (Spring AI trails Boot 4.1 — ADR 009).

4. **Privacy.** Only aggregate figures leave the boundary to the model — month, currency, per-category totals/shares, top merchant descriptions. No individual line items, account numbers, balances, or identity. Off by default; opt-in with a key.

## Consequences

- **Positive:** the AI read-model gets a proper home without touching the transaction hot path; finance-service stays the single source of truth (insight-service can't over-read — the forwarded JWT bounds it); the template fallback means the feature degrades to still-useful numbers and never hard-depends on Claude; the Anthropic client + fallback pattern is now proven twice and ready to extract to a shared module if a third user appears.
- **Negative / cost:** a fourth service to run and deploy; a synchronous fan-out (insight → finance → Claude) adds latency to a summary request (bounded by timeouts + the fallback); another Anthropic client duplicated for now (deliberate — no shared lib yet). Summaries are non-deterministic when AI is on; an eval set is future work.
- **Revisit** with NL Q&A (Claude **tool use** against finance-service endpoints), an eval harness, caching of summaries, and extracting the shared Anthropic client if a third caller lands.
