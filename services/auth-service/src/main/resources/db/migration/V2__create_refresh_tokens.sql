-- Refresh tokens: opaque 256-bit values handed to clients; only the SHA-256
-- hash is stored (a DB leak must not yield usable tokens).
-- `replaced_by` builds the rotation chain now so the refresh endpoint can add
-- reuse detection (stolen-token defense) without a schema change.

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    token_hash   VARCHAR(64) NOT NULL,     -- hex SHA-256 of the raw token
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ,              -- set on logout or reuse detection
    replaced_by  UUID REFERENCES refresh_tokens (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- logout-all / cleanup queries scan by user
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
