# FinTrack — the product

The domain-specific half of this repo: a **household personal-finance platform** for a family or an individual. The authentication/identity plumbing it sits on is generic and documented separately in [TEMPLATE.md](TEMPLATE.md).

## What FinTrack is

A tool for tracking a household's money — accounts, income, spending, budgets, and loans — where multiple members each contribute under their own profile, viewable per-member or as a household total. It works equally for a single person: you're just a one-member household.

The distinctive product stance is **privacy-first sharing** (see [ADR 001](decisions/001-personal-first-privacy.md), [ADR 006](decisions/006-shared-commitments.md)): every transaction is `personal` by default, and sharing to the household is a deliberate opt-in. This avoids the "financial infidelity" failure mode where forced transparency makes people stop logging honestly. The household value proposition is **shared budgets and bills**, not full expense transparency.

## Domain model

- **Household** — the top-level grouping. Signup auto-creates a single-member household; the owner can invite others by email.
- **Member** — a user's membership in a household, with a **role**: `OWNER` / `ADULT` / `CHILD`.
- **Account** — a place money lives (checking, savings, card…); auto-created from imported statements.
- **Transaction** — amount (`NUMERIC(19,4)` + currency, never float), date, merchant, a raw bank category, a **canonical category** (ADR 008), and a **`visibility`** flag (`personal` | `shared`, default `personal`).
- **Budget** — household income / expense / savings lines by frequency, rolled up to budget-vs-actual.
- **Income profile** (per member) — salary, super/pension, tax withheld; store the payslip figures. Tax *estimation* is a later AI feature, since rules change yearly.
- **Home loan** — mortgage details feeding a payoff calculator.

Every finance table is scoped by `household_id` + `member_id` from its first migration — even with one member, because retrofitting that scoping touches every table and query.

## What you can do today

- **Import a bank CSV** → transactions are deduplicated, accounts created from the file, and the **dashboard** summarizes spend by category, month, and merchant.
- **Categorize** with Claude (opt-in, ADR 009) into the canonical taxonomy, with a rule-based fallback and a one-click **recategorize** over existing rows.
- **Budget vs actual** — set a household budget and compare planned vs actual, in totals and **per canonical category**.
- **Model a home loan** — payoff time, total interest, and how extra repayments change both.
- **See cash flow & affordability** from per-member income profiles.
- **Share commitments** — opt specific transactions into a household `shared` view with an equal-split settlement, without exposing personal spending.

## Authorization

finance-service verifies auth-service JWTs via JWKS and scopes **every** query by the JWT's `householdId` / `memberId` claims — never by client-supplied parameters (`AuthenticatedMember` in finance-service). A member can only read their own transactions and the household's `shared` items; personal rows of other members are structurally unreachable in the shared query ([ADR 006](decisions/006-shared-commitments.md)).

Roles (`OWNER` / `ADULT` / `CHILD`) are carried in the token; finer role-based visibility (children restricted to their own data, adults to shared + aggregate totals) is a planned refinement — see [ROADMAP.md](ROADMAP.md).

## AI

**Transaction categorization** ships in finance-service (ADR 009): Claude classifies rows into the canonical taxonomy behind a pluggable port, opt-in, with a deterministic fallback. A dedicated **insight-service** (monthly summaries, natural-language Q&A via tool use, a labeled tax *estimate*) is planned, reusing the same Anthropic client pattern.

## More

Full plan: [ROADMAP.md](ROADMAP.md). Architecture and trade-offs: [ARCHITECTURE.md](ARCHITECTURE.md). Endpoints: [API.md](API.md).
