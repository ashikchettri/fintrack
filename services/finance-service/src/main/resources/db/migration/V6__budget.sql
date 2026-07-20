-- Household budget (planned income / expenses / savings), modelled on the
-- reference spreadsheet. Household-scoped (jointly held); saved wholesale
-- (replace-all), so lines are plain value rows.
--
-- Each line carries its frequency + amount; monthly/annual are derived in the
-- app. Frequencies include QUARTERLY (rates, utilities) on top of the pay
-- frequencies. Money is NUMERIC(19,4) + a currency code.

CREATE TABLE budget_lines (
    id           UUID PRIMARY KEY,
    household_id UUID          NOT NULL,
    section      VARCHAR(16)   NOT NULL,
    category     VARCHAR(60),
    name         VARCHAR(120)  NOT NULL,
    frequency    VARCHAR(16),
    amount       NUMERIC(19,4),
    sort_order   INT           NOT NULL DEFAULT 0,
    currency     VARCHAR(3)    NOT NULL DEFAULT 'AUD',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_budget_lines_section
        CHECK (section IN ('INCOME', 'EXPENSE', 'SAVING')),
    CONSTRAINT ck_budget_lines_frequency
        CHECK (frequency IS NULL OR frequency IN ('WEEKLY', 'FORTNIGHTLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    CONSTRAINT ck_budget_lines_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_budget_lines_household ON budget_lines (household_id, sort_order);
