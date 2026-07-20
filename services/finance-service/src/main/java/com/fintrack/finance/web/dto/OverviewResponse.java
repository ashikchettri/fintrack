package com.fintrack.finance.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The dashboard rollup — the household's plan (from the budget) vs reality
 * (from actual transactions), so the dashboard is more than an imported bank
 * statement. Actuals are the caller's most recent month with data.
 *
 * <p>Both totals and a per-category breakdown: since ADR 008 both budget lines
 * and transactions map to the canonical {@code SpendingCategory} vocabulary, the
 * plan and the actuals finally share a language and can be compared per category.
 */
public record OverviewResponse(
        String currency,
        boolean hasBudget,
        String actualMonth,        // "2026-07" or null if no transactions yet
        Planned planned,
        Actual actual,
        List<CategoryComparison> byCategory) {

    /** Monthly budget figures. */
    public record Planned(BigDecimal income, BigDecimal expenses, BigDecimal savings, BigDecimal leftover) {
    }

    /** Actuals for {@code actualMonth}. */
    public record Actual(BigDecimal income, BigDecimal expenses) {
    }

    /** Planned vs actual for one canonical expense category (label = its heading). */
    public record CategoryComparison(String category, BigDecimal planned, BigDecimal actual) {
    }
}
