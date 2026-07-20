# ADR 004 — Email verification with short numeric codes

**Status:** Accepted · 2026-07-12

## Context

Signup should prove mailbox ownership before an account can log in. Interim
sender is a dedicated Gmail account; a custom domain (and likely a transactional
provider) comes later.

## Decision

1. **Code, not link**: a 4-digit numeric code typed into the UI — simplest UX,
   works with the SPA without deep-linking. Length is configurable
   (`fintrack.auth.verification.code-length`); consider 6 when the stakes rise.
2. **Compensating controls for the small keyspace** (10⁴ combos): the code is
   stored as a SHA-256 hash, expires after 15 minutes, and is voided after
   **5 failed attempts** (a new code must be requested). Resends have a 60s
   cooldown per email; one active code per user (resend replaces).
3. **Login requires a verified email** — distinct problem type
   (`email-not-verified`, 403) so the UI routes to the verify screen. Existing
   users are grandfathered as verified by the migration.
4. **No enumeration**: verify/resend respond identically whether or not the
   email has an account.
5. **Transport**: `EmailSender` abstraction. `local` profile → **Mailpit**
   (compose service; SMTP :1025, web inbox + REST API :8025 — Playwright e2e
   reads codes from it). Other profiles → SMTP via env
   (`MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD`), initially Gmail with an App
   Password. No credentials in git.

## Consequences

- Positive: mailbox ownership proven; dev loop needs no real mailbox; swap to
  a transactional provider later is a config change, not a code change.
- Negative: signup gains a step (industry-standard friction); Gmail sending
  limits (~500/day) are fine now but a real provider is needed before any
  public use; login gate means abandoned signups can't log in (resend exists).

## Update — 2026-07-13

Code length raised to **6 digits**, matching the password-reset flow — one
keyspace, one property (`fintrack.auth.verification.code-length`), consistent UX.
