-- Accounts (schema: finance). Household-scoped from column one — even with a
-- single member today, retrofitting the scoping later touches every query
-- (ARCHITECTURE.md §6). member_id is who created/owns the account.
--
-- No stored balance: the current balance is derived (opening_balance + the
-- account's transactions) once transactions land, so there's no denormalized
-- figure to keep in sync.

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    household_id    UUID          NOT NULL,
    member_id       UUID          NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    type            VARCHAR(16)   NOT NULL,
    currency        VARCHAR(3)    NOT NULL,          -- ISO 4217 (varchar, not char: bpchar fails Hibernate validation)
    opening_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_accounts_type
        CHECK (type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'CASH', 'INVESTMENT', 'OTHER')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

-- every read is scoped by household + member
CREATE INDEX idx_accounts_household_member ON accounts (household_id, member_id);
