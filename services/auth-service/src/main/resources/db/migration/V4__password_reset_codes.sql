-- Password reset codes (ADR 005): mirrors email_verification_codes but with
-- its own lifecycle — reset codes are 6-digit and their consumption has
-- side effects (session revocation, verified flag).

CREATE TABLE password_reset_codes (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    code_hash    VARCHAR(64) NOT NULL,     -- hex SHA-256 of the numeric code
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at  TIMESTAMPTZ,
    CONSTRAINT uq_password_reset_codes_user UNIQUE (user_id)
);
