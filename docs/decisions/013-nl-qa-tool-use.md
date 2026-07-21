# ADR 013 — Natural-language Q&A via Claude tool use

**Status:** Accepted · 2026-07-21

## Context

insight-service v1 (ADR 012) ships a monthly summary. The next slice is the one that feels like magic: **ask a question in plain English** — "how much did I spend on food in June?" — and get a grounded answer. This can't be a fixed prompt: the model has to *decide what data it needs*, fetch it, and reason over the real figures. That's exactly what **tool use** (function calling) is for.

Unlike the summary, there is **no meaningful deterministic fallback** — interpreting an arbitrary question and orchestrating data access *is* the LLM's job. So Q&A is only available when AI is configured; without a key it returns a clear "not configured" rather than a degraded guess.

## Decision

1. **`POST /api/v1/insights/ask` `{question}` → `{question, answer}`.** The answer is grounded in the caller's real finance data via a Claude **tool-use loop**.

2. **The agentic loop lives in `QuestionService`.** Claude is given the question + a small tool set; it decides which tools to call; insight-service executes them against finance-service (forwarding the caller's JWT, ADR 012) and feeds the results back; Claude produces the final answer. Bounded to a few turns so a runaway conversation can't loop forever.

3. **One tool for v1: `get_spending(month?)`** — returns the caller's totals, spend by canonical category, top merchants, and the list of available months, for a month (`YYYY-MM`) or all-time. It reuses finance-service's dashboard, so the model works from the same figures the UI shows, in the canonical taxonomy (ADR 008) — "food" resolves to "Groceries & Food". More tools (budget vs actual, transaction search) are additive later.

4. **AI-required, gracefully.** `QuestionService` resolves the Anthropic client through an `ObjectProvider`; if AI is off (no client bean) it throws `AiNotConfiguredException` → **503**, so the endpoint exists but says so plainly. The monthly summary keeps its template fallback; Q&A does not.

5. **Privacy (unchanged from ADR 012).** Only what a tool returns — aggregate figures and merchant descriptions from finance-service, itself scoped by the forwarded JWT — reaches the model. No raw line items beyond what `get_spending` aggregates, no identity.

## Consequences

- **Positive:** the headline "ask anything" experience, grounded in real data (the model can't answer without calling a tool, so it can't invent figures unchallenged); finance-service stays the source of truth and the scoping authority; the tool set is a clean extension point — new questions become new tools, not new endpoints.
- **Negative / cost:** multiple model round-trips per question (each tool call is a turn) — more latency and tokens than the summary; the loop is hand-rolled against Anthropic's message/tool protocol (contained in one class; a Spring AI / agent abstraction is the future cleanup); non-deterministic answers, so an eval set matters before this is trusted broadly; a mid-conversation finance outage surfaces as a 502.
- **Revisit** with more tools (budget adherence, "find transactions like…"), answer caching, streaming responses, and an eval harness; and to swap the hand-rolled loop for Spring AI's tool-calling once it supports Boot 4.1.
