package com.fintrack.finance.web.dto;

import java.math.BigDecimal;

/**
 * The dashboard rollup — the household's plan (from the budget) vs reality
 * (from actual transactions), so the dashboard is more than an imported bank
 * statement. Actuals are the caller's most recent month with data.
 *
 * <p>Kept to totals: budget categories (e.g. "Groceries & Food") and bank
 * transaction categories (e.g. "Food & Drink") use different taxonomies, so a
 * per-category comparison would mislead — the monthly totals line up cleanly.
 */
public record OverviewResponse(
        String currency,
        boolean hasBudget,
        String actualMonth,        // "2026-07" or null if no transactions yet
        Planned planned,
        Actual actual) {

    /** Monthly budget figures. */
    public record Planned(BigDecimal income, BigDecimal expenses, BigDecimal savings, BigDecimal leftover) {
    }

    /** Actuals for {@code actualMonth}. */
    public record Actual(BigDecimal income, BigDecimal expenses) {
    }
}
