-- Remove the standalone per-member income table (V5). Household income is now
-- captured by the budget's INCOME lines on "Income & expenses" (budget_lines),
-- which cash flow reads directly — the separate salary form is retired.
DROP TABLE IF EXISTS finance.incomes;
