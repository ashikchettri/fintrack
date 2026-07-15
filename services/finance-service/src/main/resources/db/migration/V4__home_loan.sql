-- Home-loan profile (household-scoped). Unlike transactions (member-scoped), a
-- home loan belongs to the whole household — it's jointly held — so it's keyed
-- by household_id only, one profile per household.
--
-- This is the first slice of the household's financial profile: these figures
-- (loan, interest, repayments, offset) feed the cash-flow + affordability
-- calculations later ("can we afford a $500k investment property?"). Income
-- (salary/super/bonus) comes in its own screen next.
--
-- Money is NUMERIC(19,4) + a currency code; interest is an annual percentage.

CREATE TABLE home_loans (
    id                  UUID PRIMARY KEY,
    household_id        UUID          NOT NULL UNIQUE,   -- one profile per household
    has_home_loan       BOOLEAN       NOT NULL DEFAULT false,
    lender              VARCHAR(100),
    loan_amount         NUMERIC(19,4),
    interest_rate       NUMERIC(6,4),                    -- annual %, e.g. 6.2500
    repayment_frequency VARCHAR(16),
    repayment_amount    NUMERIC(19,4),
    has_offset          BOOLEAN       NOT NULL DEFAULT false,
    offset_balance      NUMERIC(19,4),
    ownership           VARCHAR(16),                     -- JOINT (both names) / SOLE
    currency            VARCHAR(3)    NOT NULL DEFAULT 'AUD',
    notes               VARCHAR(500),
    updated_by          UUID,                            -- member who last edited
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_home_loans_frequency
        CHECK (repayment_frequency IS NULL OR repayment_frequency IN ('WEEKLY', 'FORTNIGHTLY', 'MONTHLY')),
    CONSTRAINT ck_home_loans_ownership
        CHECK (ownership IS NULL OR ownership IN ('JOINT', 'SOLE')),
    CONSTRAINT ck_home_loans_currency CHECK (currency ~ '^[A-Z]{3}$')
);
