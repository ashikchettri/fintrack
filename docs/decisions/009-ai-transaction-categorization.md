# ADR 009 — AI transaction categorization

**Status:** Accepted · 2026-07-20

## Context

ADR 008 gave us a canonical `SpendingCategory` vocabulary and a rule-based `CategoryMapper.fromBankCategory` that maps a bank export's free-text category onto it. The rules are a decent baseline but brittle: they mis-bucket anything the keyword list doesn't anticipate into `OTHER`, and they lean on the bank having *supplied* a category at all — many statements don't, leaving only a cryptic description (`SQ *THE COFFEE CLUB`, `OSKO PAYMENT 4471`).

The hero flow is "upload CSV → instant dashboard". The multiplier is **instant *and correctly categorized***. That's a classification problem an LLM is good at, and ADR 008 deliberately set up the target: a fixed enum to emit and evaluate against. This is the transaction-categorization slice of FinTrack's AI work, shipped here; the rest — monthly summaries and NL Q&A in a dedicated `insight-service` — is still ahead.

## Decision

1. **Categorization lives in finance-service, behind a `TransactionCategorizer` seam** — it's a transaction write-path concern that needs the row in hand, not an aggregate insight, so a cross-service hop per import would be all cost and no benefit. The interface takes a batch and returns one `SpendingCategory` per input:
   - `RuleBasedCategorizer` (default, always present) wraps `CategoryMapper` — unchanged behaviour from ADR 008.
   - `ClaudeCategorizer` (`@Primary` when enabled) calls the model, and **falls back to the rule-based result per item** on any error, timeout, malformed response, or unknown category. The rules are the floor; the AI only ever lifts a row off `OTHER`.
   The import path now injects the interface instead of calling `CategoryMapper` directly. `insight-service` (summaries, Q&A) remains the separate Phase-4 service.

2. **Direct Anthropic Messages API via Spring `RestClient`, not Spring AI.** The architecture names Spring AI as the intended integration, and it still is — but Spring AI (like Spring Cloud, which we hit in ADR 007) trails Spring Boot, and this repo is on Boot 4.1. Rather than pin or gamble a compatibility, the model call sits behind an `AnthropicChatClient` port with a thin RestClient implementation. When a Boot-4.1-compatible Spring AI ships, it drops in behind the port without touching any caller. Model: **Claude Haiku** — classification is exactly its sweet spot, and it keeps per-import cost negligible.

3. **Structured output by contract + defensive parsing.** The prompt pins the model to the enum ("classify each into EXACTLY ONE of …; respond with ONLY a JSON array `[{i, category}]`"). The response is parsed leniently (extract the array, validate each `category` against the enum) and any row the model omits or mislabels keeps its rule-based category. Batched per import (chunked) so it's one call, not N. Tool-use / JSON-schema-enforced output is a follow-up hardening.

4. **Off by default; opt-in.** `finance.ai.categorization.enabled=false` out of the box, so the app builds, runs, and tests with **no API key** and the rule-based path — CI never calls Anthropic. Enabling it requires the flag **and** a key, validated at startup.

5. **Privacy boundary (ties to ADR 001).** Only what categorization needs leaves the boundary: the **description, the bank's category label, and the debit/credit sign**. Never the amount, account number, balance, running totals, member identity, or household. It's opt-in, and the port means the destination is swappable (a self-hosted/local model later) without changing the domain.

## Consequences

- **Positive:** imports get genuinely useful categories, not just whatever the bank guessed; the rule-based floor guarantees the feature can't be *worse* than ADR 008 and can't fail an import; the enum makes results testable and gives the AI work a ready eval target; the port keeps Spring AI (or a local model) a drop-in later.
- **Negative / cost:** a network call + latency on the import path when enabled (bounded by the fallback and a timeout); a small per-import token cost; non-determinism (mitigated by the enum contract + fallback, but categories can shift between runs); a heuristic prompt that needs an eval harness before it's trusted broadly. Transaction descriptions leave the boundary when enabled — documented and opt-in, but real.
- **Revisit** when `insight-service` lands (share the Anthropic client/config), when Spring AI supports Boot 4.1 (swap the port impl), and to harden structured output (tool use) + add an eval set and a re-categorize-existing-rows endpoint.
