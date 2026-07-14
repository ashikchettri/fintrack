-- Transactions (schema: finance). The heart of the tracker: one row per money
-- movement, always scoped by household + member (ARCHITECTURE.md §6) and tied to
-- an account. Populated manually or, for now, by CSV import (the hero feature).
--
-- Sign convention: `amount` is SIGNED — spend (debit) is negative, income
-- (credit) is positive — so the dashboard sums directly. Money is NUMERIC(19,4)
-- + a currency code; never float (CLAUDE.md).
--
-- visibility defaults to 'personal' (ADR 001): sharing within a household is
-- opt-in, so an imported statement never leaks to other members by accident.

CREATE TABLE transactions (
    id                   UUID PRIMARY KEY,
    household_id         UUID          NOT NULL,
    member_id            UUID          NOT NULL,
    account_id           UUID          NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    txn_date             DATE          NOT NULL,
    description          VARCHAR(200)  NOT NULL,
    category             VARCHAR(60),
    subcategory          VARCHAR(60),
    amount               NUMERIC(19,4) NOT NULL,          -- signed: debit < 0, credit > 0
    currency             VARCHAR(3)    NOT NULL,          -- ISO 4217 (varchar, not char: bpchar fails Hibernate validation)
    original_description  VARCHAR(300),
    tags                 VARCHAR(200),
    notes                VARCHAR(500),
    visibility           VARCHAR(16)   NOT NULL DEFAULT 'personal',
    source               VARCHAR(16)   NOT NULL DEFAULT 'MANUAL',
    import_id            UUID,                            -- groups rows from one CSV upload
    -- SHA-256 of the row's natural key (account+date+amount+description+occurrence);
    -- lets a re-uploaded or overlapping statement dedup instead of double-counting
    dedup_hash           VARCHAR(64)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_currency   CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_transactions_visibility CHECK (visibility IN ('personal', 'shared')),
    CONSTRAINT ck_transactions_source     CHECK (source IN ('MANUAL', 'CSV_IMPORT')),
    -- the same member can't hold two rows with the same natural key → idempotent import
    CONSTRAINT uq_transactions_dedup UNIQUE (household_id, member_id, dedup_hash)
);

-- dashboard + list reads are scoped by household + member, most-recent first
CREATE INDEX idx_transactions_household_member_date
    ON transactions (household_id, member_id, txn_date DESC);

-- per-account rollups (balances later)
CREATE INDEX idx_transactions_account ON transactions (account_id);
