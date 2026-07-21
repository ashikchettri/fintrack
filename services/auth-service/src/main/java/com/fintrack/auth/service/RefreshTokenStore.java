package com.fintrack.auth.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Storage seam for refresh tokens (ADR 011). The session-lifecycle operations
 * the auth services need, independent of the backing store (Postgres today,
 * Redis behind a config flag). Only the SHA-256 hash of a token is ever stored.
 */
public interface RefreshTokenStore {

    /** Issue a fresh token for a new session (login). Returns the raw token (shown to the client once). */
    String issue(UUID userId);

    /**
     * Rotate the presented token: validate it's live, consume it (single-use),
     * and issue a successor in the same session lineage. Returns the successor
     * raw token + the owning user.
     *
     * @throws InvalidRefreshTokenException on an unknown or expired token, and
     *         on <b>reuse</b> — a token presented after it was already rotated or
     *         revoked — in which case every live session for that user is also
     *         revoked (stolen-token defense, ADR 003).
     */
    Rotation rotate(String presentedRawToken);

    /** Revoke the presented token (logout). Idempotent and silent. */
    void revoke(String presentedRawToken);

    /** Revoke every live session for a user (password change/reset). Returns the count revoked. */
    int revokeAllForUser(UUID userId);

    /** Purge dead tokens older than {@code cutoff}. No-op where the store expires them itself. */
    int purgeDeadTokensOlderThan(Instant cutoff);

    /** The outcome of a successful rotation. */
    record Rotation(String rawToken, UUID userId) {
    }
}
