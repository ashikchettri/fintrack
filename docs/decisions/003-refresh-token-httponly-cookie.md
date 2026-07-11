# ADR 003 — Refresh token transport: httpOnly cookie

**Status:** Accepted · 2026-07-12

## Context

Phase 3 (React UI) starts now, ahead of finance-service, to verify the auth API
end-to-end. The browser needs somewhere to keep the refresh token across page
reloads. Anything readable by JavaScript (localStorage, response JSON held in
memory and persisted by app code) is exfiltratable by any XSS payload — the
worst kind of theft, since a refresh token mints new access tokens for days.

## Decision

1. The refresh token travels **only** as a cookie: `fintrack_refresh`,
   **httpOnly** (JS can never read it), **SameSite=Strict** (browsers won't
   attach it to any cross-site request, which is also our CSRF defense for the
   cookie-authenticated endpoints — no CSRF token needed), **Path=/api/v1/auth**
   (not sent with every API call, only auth flows), `Max-Age` = refresh TTL.
2. `Secure` (HTTPS-only) is on everywhere except the `local` profile
   (`fintrack.auth.refresh-cookie.secure=false` in `application-local.yml`).
3. Login and refresh responses **no longer include `refreshToken` in the JSON
   body** — if it were in the body, XSS could still steal it and the cookie
   would be theater. Refresh and logout read the cookie; a missing cookie is
   the same generic 401 as an invalid token.
4. The access token stays in the JSON body, held in JS memory only, never
   persisted — its 15-minute TTL bounds the damage of an XSS steal, and the
   silent-refresh flow (cookie-driven) restores sessions across reloads.

## Consequences

- Positive: refresh token is XSS-proof; CSRF handled by SameSite=Strict without
  token machinery; Swagger UI and the React dev proxy both work unchanged
  (same-origin, browser manages the cookie).
- Negative: non-browser API clients must manage cookies (Karate/MockMvc tests
  updated accordingly); rotation semantics unchanged but now invisible in
  response bodies, slightly harder to debug by eye.
- Revisit if a native mobile client appears (cookies are awkward there — a
  body-based variant behind a separate, rate-limited endpoint would be added).