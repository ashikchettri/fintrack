-- Shared commitments (ADR 006). No new table: a shared commitment is just a
-- transaction a member chose to expose (visibility = 'shared'), reusing the
-- column ADR 001 shipped for exactly this.
--
-- The household shared view scans a household's SHARED rows ACROSS members
-- (household_id = ? AND visibility = 'shared'). A partial index serves that
-- query and states the privacy boundary physically: personal rows are never in
-- this index, so they can't be reached by the household view.

CREATE INDEX idx_transactions_household_shared
    ON transactions (household_id, txn_date DESC)
    WHERE visibility = 'shared';
