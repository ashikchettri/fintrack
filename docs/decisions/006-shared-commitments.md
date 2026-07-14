# ADR 006 — Shared commitments & the privacy boundary

**Status:** Accepted · 2026-07-14

## Context

Importing statements, listing transactions, categorising, and generic budgeting are table stakes — every bank app does them. FinTrack should not compete with a bank at being a bank. Its defensible difference is helping the **people who share a home** coordinate money, which a bank structurally cannot do: a bank sees one customer's account, never the *agreement* between housemates about who pays for what.

The failure mode we must avoid is the one ADR 001 already names: full transaction transparency makes people stop logging ("financial infidelity"). So the product promise is deliberately narrow:

> **Coordinate shared money without giving up private money.**

A member imports a statement (all rows `personal` by default), then marks the genuinely shared costs — rent, groceries, utilities, childcare, shared subscriptions — as **shared commitments**. The household then gets a private view of *only* those shared items and the agreed totals, never anyone's personal spending.

## Decision

1. **Reuse `transactions.visibility`** (ADR 001, `personal` default). "Mark as shared commitment" = set `visibility = 'shared'`. No new "shared" entity — a shared commitment is just a transaction a member chose to expose.

2. **The privacy boundary is a query, not a filter in code.** Two access patterns, both enforced in the repository:
   - *Personal* (existing): `household_id = ? AND member_id = ?` — a member sees all of their own rows.
   - *Household shared* (new): `household_id = ? AND visibility = 'shared'` — every member sees all **shared** rows across the household, and personal rows are **structurally unreachable** (they never match the predicate). A member can only mark their *own* rows shared (the mutation is member-scoped), so exposure is always a deliberate act by the owner of the row.

3. **Contributions & settlement.** The household shared view reports, per contributing member, how much of the shared total they have covered, an **equal split** fair share, and the caller's balance (owed / owes / settled). v1 derives the member set from who has shared transactions and splits equally; **split rules** (income-based, custom weights) and a real member roster come in a later slice ([[stored as a `split_agreement`]]). The math nets to zero across members.

4. **What a shared row reveals** is only itself — date, amount, category, description, and which member paid — to the household. It never reveals the payer's other (personal) transactions. That is the whole point.

5. **Roadmap sequence** (the hero path): import → review → mark shared commitments → choose a split rule → household contribution dashboard → privacy-safe insights ("shared grocery spend rose 14%", never "your partner spent more at Coles"). Adjacent differentiators that reuse this spine: **life-event envelopes** (group spend into "Japan trip"), **subscription/price-change radar**, and **cash-flow confidence** ("safe to spend ~$X before payday").

## Consequences

- Positive: delivers a promise banks can't; honest logging is preserved (personal stays private by construction); the settlement number is the memorable "wow". The privacy boundary is testable — a test proves member B never sees member A's personal rows in the household view.
- Negative: equal-split v1 is only correct when every sharer has at least one shared row; a member who owes but paid nothing is invisible until the split-agreement slice adds an explicit roster. Contributions expose *who* paid a shared cost (intended). Member names aren't in finance-service yet, so the first UI labels the caller "You" and others generically until an auth-service member lookup lands.
- Revisit when split rules + member roster ship; the equal-split path stays as the default rule.
