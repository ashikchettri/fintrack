# ADR 001 — Personal-first privacy model

**Status:** Accepted · 2026-07-09

## Context

FinTrack supports households with multiple members (owner, partner, children). Full transaction transparency between partners creates a known behavioral failure mode: members under-report or stop logging expenses they don't want seen ("financial infidelity" problem, well documented in shared-finance apps). An app that discourages honest logging fails at its core purpose.

## Decision

1. Every transaction defaults to `visibility = personal`. Sharing to the household is a deliberate, per-transaction (or per-account) opt-in.
2. Household members see: their own data in full, transactions explicitly marked `household`, and aggregate totals only for other members.
3. Family features are framed around **shared budgets and bills** (mortgage, groceries, utilities, family budget), not full expense transparency.
4. `CHILD` role members see only their own data.
5. Household schema (`household_id`, `member_id`, `visibility`) still ships in phases 1–2 — the columns are nearly free now and prohibitively expensive to retrofit.

## Consequences

- Positive: honest logging isn't penalized; family value prop ("see our shared finances") survives; the privacy boundary is enforced in the query, not bolted on afterward.
- Negative: "family total spending" is only as accurate as what members share; aggregate totals partially reveal spending levels (accepted — totals without line items is the standard compromise).
- Revisit as family features mature with real usage experience; dropping family features entirely remains a zero-cost option.
