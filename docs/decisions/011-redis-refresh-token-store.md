# ADR 011 — Redis refresh-token store

**Status:** Accepted · 2026-07-20

## Context

Refresh tokens live in Postgres (`auth.refresh_tokens`): the SHA-256 hash of each opaque token, a rotation chain (`replaced_by`), and revocation timestamps. The refresh endpoint is single-use with **reuse detection** — a revoked token presented again revokes every live session for the user (ADR 003). A daily job purges dead rows after a 30-day audit window.

This works, but refresh tokens are ephemeral session state on the hottest auth path, and Postgres is the wrong home for them long-term:

- **Expiry wants to be native.** Redis `EXPIRE` retires tokens automatically; the Postgres model needs a scheduled `DELETE` job.
- **It's write-heavy churn.** Every login and every refresh writes/rotates a row; that contends with the real relational data (users, households).
- **It scales with sessions, not the domain.** Session state belongs in a session store. Redis is already in the stack for the gateway rate limiter (ADR 007).

The catch: rotation + reuse detection is **security-critical and must stay atomic**. A naive "delete the token on rotation" loses reuse detection (a replayed token then looks merely unknown). The move only makes sense if Redis preserves every behavior the Postgres store has today.

## Decision

1. **Introduce a `RefreshTokenStore` seam** over the five operations the services need — `issue` (login), `rotate` (validate + reuse-detect + consume + issue successor), `revoke` (logout), `revokeAllForUser` (password change/reset), and `purgeDeadTokensOlderThan` (housekeeping). Every caller (`LoginService`, `RefreshService`, `ChangePasswordService`, `PasswordResetService`, `TokenMaintenanceService`) depends on the interface, not the JPA repository/entity.

2. **`JpaRefreshTokenStore` stays the default** and preserves today's behavior exactly (it's the current logic, relocated behind the interface). Selected by `fintrack.auth.refresh-token.store=jpa` (default). This lets the seam land as a pure, provable no-behavior-change refactor before any Redis risk.

3. **`RedisRefreshTokenStore` is opt-in** (`...store=redis`) and reproduces the Postgres semantics on Redis:
   - **Keys.** `rt:{tokenHash}` → a hash `{ user, family, state, exp }` (`state` ∈ `active|used|revoked`, `exp` = the real token expiry); `rtu:{userId}` → set of family ids for the user; `rtf:{familyId}` → set of token hashes in a session lineage. All keys carry a Redis TTL of the **30-day audit window**, so a replayed token is still present (and detectable as reuse) exactly as long as the Postgres store keeps a dead row — token *expiry* is checked against the stored `exp`, revocation against `state`, in that order (revoked-before-expired, matching JPA).
   - **Atomicity via Lua.** `rotate`, `revoke`, and `revokeAllForUser` run as Lua scripts — Redis executes each atomically, so "validate active → mark used → store successor" and the reuse cascade ("revoke every token in every family for this user") can't interleave. Rotation is single-use by construction: a second concurrent rotate finds `state != active` and triggers the reuse response.
   - **`purge` is a no-op** — Redis TTL does the housekeeping the daily job did.

4. **Only the hash leaves the client once; only the hash is stored** — unchanged. Redis holds the same SHA-256 digests, never raw tokens.

## Consequences

- **Positive:** native TTL retires tokens (no purge job on the Redis path); the hot login/refresh path leaves Postgres; session state sits in a session store; Redis earns a second use after the gateway. The seam means the two stores are swappable by config and independently testable, and the migration is reversible.
- **Negative / limits:** two stores to reason about; the Redis reuse-detection logic lives in Lua rather than a SQL transaction (tested against a real Redis via Testcontainers). The Lua scripts touch multiple non-co-located keys, so they assume a **single Redis (or a primary), not Redis Cluster** — cluster support would hash-tag keys by user; noted, not needed now. Switching stores doesn't migrate existing sessions — a cutover logs everyone out once (acceptable for refresh tokens).
- **Rollout:** land the seam with JPA default (no behavior change), then the Redis implementation + Testcontainers tests, then flip the default once verified. The Postgres table and its store remain as the fallback.
- **Revisit** with the observability work (session metrics) and if multi-region Redis (Cluster) is ever needed.
