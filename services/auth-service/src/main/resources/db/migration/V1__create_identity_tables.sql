-- Identity tables for auth-service (schema: auth — set via flyway.default-schema).
-- Household model ships now even though phase 1 has single-member households:
-- retrofitting household scoping later touches every table and query (ARCHITECTURE.md §6).

CREATE TABLE households (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    -- stored lowercase (normalized in the service layer); 320 = max legal email length
    email          VARCHAR(320) NOT NULL,
    password_hash  TEXT         NOT NULL,   -- Argon2id encoded string, length varies with params
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE household_members (
    id            UUID PRIMARY KEY,
    household_id  UUID        NOT NULL REFERENCES households (id),
    user_id       UUID        NOT NULL REFERENCES users (id),
    role          VARCHAR(16) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_household_members_role CHECK (role IN ('OWNER', 'ADULT', 'CHILD')),
    -- a user joins a given household at most once
    CONSTRAINT uq_household_members_household_user UNIQUE (household_id, user_id)
);

-- membership lookups by user (login → resolve household + role for JWT claims)
CREATE INDEX idx_household_members_user_id ON household_members (user_id);