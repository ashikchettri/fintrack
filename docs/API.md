# API reference — auth-service

Base path `/api/v1`. All errors are RFC 9457 `ProblemDetail` (`application/problem+json`) carrying `type`, `title`, `status`, `detail`, a `traceId`, and — on validation failures — a field→message `errors` map. Every response echoes an `X-Request-Id` correlation header. Interactive docs: `/swagger-ui.html`.

## Authentication model

- **Access token**: RS256 JWT, 15-min TTL, returned in the login/refresh body, held in browser memory only. Claims: `sub` (userId), `householdId`, `memberId`, `role`, `iss`.
- **Refresh token**: opaque, 7-day, rotated on every use with reuse detection; delivered **only** as an httpOnly `SameSite=Strict` cookie scoped to `/api/v1/auth` (ADR 003). Never in a response body.
- **Verification**: public keys at `GET /.well-known/jwks.json` — downstream services verify JWTs without a shared secret.

## Public endpoints

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/auth/signup` | `{email, password}` | 201 profile | password 12–128 chars; creates a single-member household (OWNER); emails a 6-digit verification code |
| POST | `/auth/verify-email` | `{email, code}` | 204 | 5-attempt cap, 15-min TTL |
| POST | `/auth/resend-verification` | `{email}` | 204 | always 204 (no enumeration); 60s cooldown |
| POST | `/auth/login` | `{email, password}` | 200 `{accessToken, tokenType, expiresInSeconds}` + refresh cookie | 401 generic; **403** `email-not-verified`; **429** throttled (5 fails / 15 min) |
| POST | `/auth/refresh` | — (refresh cookie) | 200 + rotated cookie | reuse of a rotated token revokes all sessions |
| POST | `/auth/logout` | — (refresh cookie) | 204 + cleared cookie | idempotent |
| POST | `/auth/forgot-password` | `{email}` | 204 | always 204 (no enumeration) |
| POST | `/auth/reset-password` | `{email, code, newPassword}` | 204 | 6-digit code; revokes all sessions; marks email verified |
| POST | `/households/invites/accept` | `{email, code, password, name}` | 201 profile | joins the inviting household as ADULT; email pre-verified by the invite; 400 `invalid-invite` |
| GET | `/.well-known/jwks.json` | — | 200 JWKS | public keys only |

## Authenticated endpoints (Bearer access token)

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/users/me` | — | 200 profile | identity from the JWT `sub` |
| POST | `/users/me/password` | `{currentPassword, newPassword}` | 204 | verifies current password; revokes other sessions |
| POST | `/users/me/email` | `{newEmail, currentPassword}` | 204 | emails a code to the NEW address; old stays active |
| POST | `/users/me/email/verify` | `{code}` | 204 | confirms and swaps the login email |
| POST | `/households/invites` | `{email}` | 202 | OWNER-only (else **403** `not-household-owner`); emails a 72h invite code; 409 if the email already has an account |
| GET | `/households/members` | — | 200 `[{memberId, name, role, isYou}]` | the caller's household roster (names for the shared view) |

## Problem types

`https://fintrack.example/problems/…`: `validation-error` (400), `invalid-credentials` (401), `email-not-verified` (403), `too-many-attempts` (429), `email-already-in-use` (409), `invalid-verification-code` / `invalid-reset-code` / `incorrect-current-password` / `invalid-invite` (400), `not-household-owner` (403).

## finance-service

`/api/v1/**` requires a Bearer access token, verified against auth-service's JWKS (`AUTH_JWKS_URI`). The `role` claim maps to a `ROLE_*` authority. `AuthenticatedMember` exposes `userId`/`householdId`/`memberId`/`role` from the token — every household-scoped query must filter by these, never by client-supplied parameters.

### Endpoints

| Method & path | Purpose |
|---|---|
| `POST /api/v1/accounts` · `GET /api/v1/accounts` · `GET/DELETE /api/v1/accounts/{id}` | Accounts CRUD, household+member scoped |
| `POST /api/v1/imports/transactions` (multipart `file`, `currency`) | Import a bank CSV → transactions + auto-created accounts, deduped |
| `GET /api/v1/dashboard[?month=YYYY-MM]` | Dashboard read model: totals, by-category, by-month, top merchants, recent. `month` scopes the snapshot (the trend stays all-time); response carries `availableMonths` |
| `GET /api/v1/transactions` | The caller's transactions (scoped) |
| `PATCH /api/v1/transactions/{id}/visibility` (`{"visibility":"shared\|personal"}`) | Mark/unmark a shared commitment (ADR 006); member-scoped |
| `GET /api/v1/household/shared[?month=YYYY-MM]` | Private household view of shared commitments — only shared items + agreed totals + suggested settlement, never personal spending (ADR 006) |
| `GET · PUT /api/v1/household/home-loan` | The household's home-loan profile (jointly held → **household-scoped**, not member-scoped). Upserted; feeds the cash-flow + affordability calculations |

**Privacy boundary (ADR 006):** personal queries filter `household_id + member_id`; the household shared view filters `household_id + visibility = 'shared'` across members, so personal rows are structurally unreachable.
