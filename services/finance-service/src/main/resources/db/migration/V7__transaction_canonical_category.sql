-- Canonical spending category (ADR 008): the normalised category a transaction
-- maps to, drawn from the fixed SpendingCategory vocabulary. Set at import time;
-- the raw `category` column is kept for provenance and the spend donut. Nullable
-- so pre-existing rows are unaffected — the rollup canonicalises those live.
ALTER TABLE transactions ADD COLUMN canonical_category VARCHAR(40);

-- Rollups group the latest month by canonical category per member.
CREATE INDEX idx_transactions_canonical
    ON transactions (household_id, member_id, canonical_category);
