# FinTrack — the product

The domain-specific half of this repo: a **household personal-finance tracker** for a family or an individual. The authentication/identity plumbing it sits on is generic and documented separately in [TEMPLATE.md](TEMPLATE.md).

## What FinTrack is

A tool for tracking a household's money — accounts, income, spending, and budgets — where multiple members (owner, partner, children) each contribute under their own profile, viewable per-member or as a family total. It works equally for a single person: you're just a one-member household.

The distinctive product stance is **privacy-first sharing** (see [ADR 001](decisions/001-personal-first-privacy.md)): every transaction is `personal` by default, and sharing to the household is a deliberate opt-in. This avoids the "financial infidelity" failure mode where forced transparency makes people stop logging honestly. The family value proposition is **shared budgets and bills**, not full expense transparency.

## Domain model

- **Household** — the top-level grouping. Signup auto-creates a single-member household.
- **Member** — a user's membership in a household, with a **role**: `OWNER` / `ADULT` / `CHILD`.
  - `OWNER` / `ADULT`: see their own data in full, transactions explicitly marked `household`, and aggregate totals for others.
  - `CHILD`: sees only their own data.
- **Account** — a place money lives (checking, savings, card…). *Phase 2.*
- **Transaction** — amount (`NUMERIC(19,4)` + currency, never float), date, merchant, category, and a **`visibility`** flag (`personal` | `household`, default `personal`). *Phase 2.*
- **Budget** — a spending target, shareable to the household. *Phase 2.*
- **Income profile** (per member) — salary, super/pension, tax withheld; store the payslip figures. Tax *estimation* is a later AI feature, since rules change yearly. *Phase 7.*

Every finance table is scoped by `household_id` + `member_id` from its first migration — even with one member, because retrofitting that scoping touches every table and query.

## Authorization (the interesting part)

finance-service verifies auth-service JWTs via JWKS and authorizes on **policy** (role + visibility), not just ownership:

- adults see `household` items + aggregate totals only — never each other's `personal` line items;
- children see only their own data;
- the "family view" is shared budgets/bills + per-member totals, not a shared ledger.

Household-scoped queries **always** filter by the JWT's `householdId`/`memberId` claims, never by client-supplied parameters (`AuthenticatedMember` in finance-service).

## AI features (Phase 4)

insight-service (Spring AI + Claude): auto-categorize transactions, monthly spending summaries, natural-language Q&A ("how much on food in June?") via tool-use against finance-service, and a clearly-labeled tax *estimate*.

## Status & roadmap

Phase 1 (identity/auth) is complete; the finance domain (accounts → transactions → budgets) is **Phase 2**, next up. Full plan: [ROADMAP.md](ROADMAP.md). Architecture and trade-offs: [ARCHITECTURE.md](ARCHITECTURE.md).
