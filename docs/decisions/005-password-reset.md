# ADR 005 — Password reset via emailed one-time code

**Status:** Accepted · 2026-07-12

## Context

Users forget passwords. The reset path is the classic account-takeover target,
so it gets the strictest treatment of our OTP flows (which follow ADR 004).

## Decision

1. **6-digit code** (vs 4 for signup verification): reset grants full account
   control, so it gets the larger keyspace. Same compensating controls:
   SHA-256 at rest, 15-minute TTL, dead after 5 wrong attempts, 60s resend
   cooldown, one active code per user.
2. **`POST /auth/forgot-password` is always 204** — reveals nothing about
   account existence.
3. **`POST /auth/reset-password` (email + code + new password)**, on success:
   - new Argon2id hash (same 12–128 policy as signup);
   - **every active refresh token is revoked** — a thief holding stolen
     sessions is evicted the moment the owner resets;
   - the email is marked **verified** (typing an emailed code is mailbox
     proof) — un-verified users who forgot their password unstick in one step;
   - the login throttle for that email is cleared.
4. Wrong/expired/over-attempted code → the same generic 400 as verification.

## Consequences

- Positive: complete self-service recovery; stolen sessions die on reset;
  reuses the ADR 004 mail transport (Mailpit locally, Gmail interim).
- Negative: two OTP tables/services with similar shapes (deliberate — merging
  them behind a "purpose" column couples unrelated lifecycles; revisit if a
  third OTP flow appears).
