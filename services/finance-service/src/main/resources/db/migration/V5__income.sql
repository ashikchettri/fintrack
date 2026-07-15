-- Income (per-member). Each household member records their own income; the
-- household cash-flow totals across members. Member-scoped (one row per member
-- per household) — unlike the home loan, which is a single jointly-held record.
--
-- Salary is stored with its pay frequency and normalized to an annual figure
-- for the household total. Super (employer contribution) is captured as an
-- annual rate for completeness — it isn't spendable cash flow.

CREATE TABLE incomes (
    id                  UUID PRIMARY KEY,
    household_id        UUID          NOT NULL,
    member_id           UUID          NOT NULL,
    salary_amount       NUMERIC(19,4),
    salary_frequency    VARCHAR(16),
    super_rate          NUMERIC(6,4),                    -- employer super %, e.g. 11.5000
    bonus_annual        NUMERIC(19,4),
    other_income_annual NUMERIC(19,4),
    other_income_note   VARCHAR(200),
    currency            VARCHAR(3)    NOT NULL DEFAULT 'AUD',
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_incomes_member UNIQUE (household_id, member_id),
    CONSTRAINT ck_incomes_frequency
        CHECK (salary_frequency IS NULL OR salary_frequency IN ('WEEKLY', 'FORTNIGHTLY', 'MONTHLY', 'ANNUALLY')),
    CONSTRAINT ck_incomes_currency CHECK (currency ~ '^[A-Z]{3}$')
);

-- the household summary reads every member's income
CREATE INDEX idx_incomes_household ON incomes (household_id);
