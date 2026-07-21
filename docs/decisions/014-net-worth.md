# ADR 014 — Net worth (assets & liabilities)

**Status:** Accepted · 2026-07-21

## Context

The dashboard's net-position tile (PR #76) could only show the home loan against its offset — the only real balances the app tracked. A household's actual net worth needs the rest of the balance sheet: property, savings, super/investments, vehicles on one side; other loans, credit cards on the other. Transactions carry *flows*, not balances, and CSV imports don't include opening balances, so these figures can't be derived — they have to be **entered and maintained** by the household.

## Decision

1. **A single `net_worth_items` table** (Flyway `V8`), household-scoped and jointly held like the budget and home loan. Each row is `{kind: ASSET | LIABILITY, category, name, value, currency}`. One table with a `kind` (rather than separate asset/liability tables) keeps the model and the editor simple.

2. **Replace-all editing, mirroring the budget** (ADR-era `BudgetService`): `GET /api/v1/household/net-worth/items` returns the editable list, `PUT` replaces it wholesale. The UI edits the whole balance sheet and PUTs it back — no per-row lifecycle. Category and name are free text with UI suggestions (Property, Savings & cash, Investments, Super, Vehicle / Mortgage, Personal loan, Credit card, …); value is a non-negative amount, and `kind` gives it its sign.

3. **The summary folds in the home loan so it isn't double-entered.** `GET /api/v1/household/net-worth` returns totals + a breakdown that includes the manual items **plus** the existing home loan as a derived liability (its balance) and its offset as a derived asset. Derived lines are tagged `source: HOME_LOAN` (vs `MANUAL`) so the UI can mark them read-only ("from your home loan") and the user knows not to re-enter the mortgage. **Net worth = total assets − total liabilities**, across both sources.

4. **The dashboard tile becomes a real net-worth tile**, reading the summary; it stays hidden until there's at least one item or a home loan to show.

## Consequences

- **Positive:** a true, household-maintained net worth in one number, with the mortgage flowing in automatically (no double-entry); the replace-all pattern reuses proven code and a familiar editor; free-text categories keep it flexible without a taxonomy to maintain.
- **Negative / cost:** the figures are only as current as the household keeps them — a manual balance sheet drifts (a future "as of" date + reminders would help); folding the home loan into the summary couples net worth to the home-loan feature (contained in one service method); no historical net-worth trend yet (each save overwrites — snapshots over time are a follow-up).
- **Revisit** with net-worth **snapshots/trend over time**, an "as of" date per item, and — if account balances ever become trustworthy (opening balances on import, or a bank feed) — folding those in as assets too.
