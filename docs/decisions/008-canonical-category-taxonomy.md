# ADR 008 — Canonical spending taxonomy

**Status:** Accepted · 2026-07-20

## Context

Two parts of the app name categories, and they don't agree:

- **Budget lines** group planned spend under curated headings — `Housing`, `Groceries & Food`, `Transport`, … (the reference-spreadsheet groups, see `BudgetTemplate`).
- **Transactions** carry whatever the bank/aggregator export called the row — `Food & Drink`, `Transportation`, `Bills & Utilities`, … free text in `transactions.category`.

Because `"Groceries & Food" != "Food & Drink"`, the dashboard rollup (`OverviewService`) can only compare **totals** — planned vs actual income/expenses for the month. The `OverviewResponse` javadoc even apologises for it. But **per-category budget-vs-actual** ("you budgeted $1,000 for groceries, spent $1,240") is the single most useful view in a budgeting app, and it's blocked purely by the taxonomy mismatch.

This also has to be settled **before Phase 4 (AI categorization)**: if Claude is going to categorize transactions, it needs a fixed target vocabulary to emit and be evaluated against. A free-text guess per transaction is unstable and un-testable.

## Decision

1. **One canonical vocabulary, defined as a Java enum** `SpendingCategory` (`HOUSING`, `UTILITIES`, `GROCERIES`, `TRANSPORT`, `KIDS_FAMILY`, `HEALTH`, `INSURANCE_FINANCIAL`, `SUBSCRIPTIONS`, `PERSONAL`, `INCOME`, `SAVINGS`, `OTHER`). Its expense values are exactly the budget's expense groups, so budget lines map to it 1:1. `OTHER` is the always-present catch-all. It's an **enum, not a table**: the vocabulary is curated and stable, needs to be type-safe and string-match-free in code, and is the fixed set the AI will target. Per-household custom categories, if ever needed, map into this set (`OTHER` by default) — a table can come then.

2. **Both sides map to the enum, in one place** (`CategoryMapper`):
   - `fromBudgetGroup(String)` — normalises a budget line's group label to a `SpendingCategory` (the template groups match exactly; `OTHER` on miss).
   - `fromBankCategory(String category, String description)` — keyword rules over the bank category + description (e.g. *groceries/supermarket/dining* → `GROCERIES`, *fuel/uber/parking* → `TRANSPORT`). Unknown → `OTHER`. **This method is the exact seam Phase 4 replaces**: the AI emits a `SpendingCategory`, written to the same column, evaluated against the same enum.

3. **Transactions store the canonical value** in a new `canonical_category` column (Flyway `V7`), set at import time. The **original bank `category` is kept** for provenance and the spend-distribution donut — canonicalisation is a normalisation layer, not a replacement. Rows imported before `V7` (or with an unknown category) are **canonicalised live** by the rollup when the column is null, so no data backfill is required.

4. **The rollup gains a per-category breakdown.** `OverviewResponse` keeps its totals and adds `byCategory: [{category, planned, actual}]` over the union of expense categories present in the budget or the latest actual month. The dashboard's *spend-distribution* donut is left on the raw bank category for now (it's a provenance view, not a plan comparison); unifying it onto the canonical label is a cheap fast-follow.

## Consequences

- **Positive:** per-category budget-vs-actual becomes possible and honest — the two sides finally speak the same language. The AI (Phase 4) has a fixed, testable target vocabulary and a ready column to write. No migration backfill needed. The bank's own labels are preserved for provenance.
- **Negative / cost:** the keyword rules in `fromBankCategory` are a heuristic — they'll mis-bucket some rows into `OTHER` until the AI improves them, and the rule list is Australian-bank-flavoured. Adding a canonical category is a code change + deploy (the deliberate trade-off for a curated enum). Two representations of category now coexist on a transaction (raw + canonical); the invariant is *raw = what the source said, canonical = our normalisation*.
- **Revisit** when Phase 4 lands (AI replaces the keyword rules, same enum/column), and if per-household custom categories are ever needed (promote the enum to a seeded table, keeping the enum values as the system defaults).
