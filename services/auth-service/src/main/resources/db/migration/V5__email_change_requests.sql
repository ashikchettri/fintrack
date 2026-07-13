-- Email change (authenticated): the new address is proven by a code before the
-- swap, so the old address stays valid until then. Same hashed-code hardening
-- as verification/reset (ADR 004/005).

CREATE TABLE email_change_requests (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    new_email    VARCHAR(320) NOT NULL,   -- normalized lowercase, target address
    code_hash    VARCHAR(64) NOT NULL,    -- hex SHA-256 of the numeric code
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at  TIMESTAMPTZ,
    -- one pending change per user; a new request replaces the old
    CONSTRAINT uq_email_change_requests_user UNIQUE (user_id)
);
