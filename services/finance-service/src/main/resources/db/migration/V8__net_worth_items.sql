-- Household net worth (ADR 014): manually-maintained assets and liabilities.
-- Household-scoped (jointly held); saved wholesale (replace-all), so rows are
-- plain value rows without their own lifecycle. The home loan is folded into the
-- net-worth summary at read time, not stored here. Money is NUMERIC(19,4).

CREATE TABLE net_worth_items (
    id           UUID PRIMARY KEY,
    household_id UUID          NOT NULL,
    kind         VARCHAR(16)   NOT NULL,   -- ASSET | LIABILITY
    category     VARCHAR(60),
    name         VARCHAR(120)  NOT NULL,
    value        NUMERIC(19,4) NOT NULL,
    sort_order   INT           NOT NULL DEFAULT 0,
    currency     VARCHAR(3)    NOT NULL DEFAULT 'AUD',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_nwi_kind CHECK (kind IN ('ASSET', 'LIABILITY')),
    CONSTRAINT ck_nwi_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_nwi_household ON net_worth_items (household_id, sort_order);
