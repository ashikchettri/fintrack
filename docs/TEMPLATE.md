# Auth Starter Template

The domain-agnostic half of this repo: a production-grade **authentication platform** (Spring Boot API + React UI) you can lift into any new project. Everything here is generic — no finance/household logic. For the FinTrack product itself, see [PRODUCT.md](PRODUCT.md).

> **How to reuse:** clone the repo, then strip the finance-specific parts (see [What to remove](#what-to-remove-when-extracting)). The pieces below are the keepers.

## What you get

### Backend (`services/auth-service`)
Spring Boot 4.1 · Java 25 · Postgres · Flyway · Testcontainers.

| Capability | Endpoint(s) | Notes |
|---|---|---|
| Signup + email verification | `POST /auth/signup`, `/auth/verify-email`, `/auth/resend-verification` | Argon2id; 6-digit code (hashed, TTL, attempt-capped) |
| Login | `POST /auth/login` | RS256 JWT (15 min) + rotated refresh cookie; throttled |
| Silent refresh + reuse detection | `POST /auth/refresh` | rotation; replayed token revokes all sessions |
| Logout | `POST /auth/logout` | clears the cookie |
| Password reset | `POST /auth/forgot-password`, `/auth/reset-password` | emailed code; revokes sessions |
| Profile | `GET /users/me` | from the verified JWT |
| Change password / email | `POST /users/me/password`, `/users/me/email`, `/users/me/email/verify` | current-password gated |
| Key discovery | `GET /.well-known/jwks.json` | downstream services verify without a shared secret |

Cross-cutting: RFC 9457 `ProblemDetail` errors with a `traceId`, request correlation IDs (`X-Request-Id` → logs), Bean Validation, springdoc/Swagger, actuator health, RS256 key management (ADR 002), httpOnly `SameSite=Strict` refresh cookie (ADR 003), a three-provider email chain — Gmail SMTP / Resend / Mailpit (ADR 004).

### Frontend (`frontend`)
React 19 · Vite · TypeScript · Tailwind v4 · shadcn-style components · TanStack Query · react-hook-form + zod · Sonner · light/dark theme.

Pages: signup, verify-email, login, forgot/reset password, profile, account settings (change password/email). The API client keeps the access token in memory, does one silent refresh on a 401, and parses problem bodies into a typed `ApiError`. Protected routes + session bootstrap included.

### Dev experience
- `./dev.sh` — one command to check + start Postgres, Mailpit, the API, and the UI; `status` / `stop` / `mailpit` / `resend` subcommands.
- Docker Compose (Postgres + Mailpit). Gradle version catalog. Dependabot.
- CI: build + Testcontainers + Karate (backend), Vitest + Playwright mocked & e2e (frontend), gitleaks, conventional-commit + append-only-migration guards. Both Sonar gates > 90%.

## Design decisions (ADRs)

Reusable as-is: [002 JWT keys](decisions/002-jwt-key-management.md) · [003 refresh cookie](decisions/003-refresh-token-httponly-cookie.md) · [004 email verification / transport](decisions/004-email-verification.md) · [005 password reset](decisions/005-password-reset.md). (001 is product-specific — see PRODUCT.md.)

## Security model (the short version)

- **Argon2id** passwords; **RS256** JWTs (asymmetric → services verify via JWKS, no shared secret).
- Access token in JS memory only (15-min TTL); **refresh token only ever an httpOnly cookie** — XSS can't read it; `SameSite=Strict` doubles as CSRF defense.
- **Refresh rotation + reuse detection**: a replayed rotated token means theft → revoke every session.
- One-time codes (verify / reset / email-change): SHA-256 at rest, 15-min TTL, 5-attempt cap, per-request no-enumeration responses.
- Login throttling (5 fails / 15 min per email, account-agnostic). Password reset & change revoke all sessions.

## What to remove when extracting

The auth service currently bundles a minimal **household** model (FinTrack-specific). For a generic template:

1. Drop `households` / `household_members` tables and the `householdId`/`memberId` JWT claims (keep `sub` + `role`). Signup creates just a user.
2. Remove ADR 001 and the household references in signup/login services.
3. Delete `services/finance-service` and its CI matrix entry.
4. Trim the frontend brand copy (the "household" tagline in `AuthLayout`).
5. Rename packages/artifacts from `fintrack`.

Everything else — the entire auth flow, email, tokens, UI, tests, CI, `dev.sh` — carries over unchanged. **Recommended:** extract only when starting a real project #2, so the template is a proven derivative rather than a speculative fork.
