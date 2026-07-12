-- Email verification (ADR 004): users gain a verified timestamp; codes live
-- in their own table, hashed — a DB leak must not yield usable codes.

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Grandfather existing accounts: they predate verification and locking them
-- out retroactively serves nobody.
UPDATE users SET email_verified_at = now();

CREATE TABLE email_verification_codes (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    code_hash    VARCHAR(64) NOT NULL,     -- hex SHA-256 of the numeric code
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at  TIMESTAMPTZ,              -- set on successful verification
    -- one active code per user: resend replaces (delete + insert)
    CONSTRAINT uq_email_verification_codes_user UNIQUE (user_id)
);
