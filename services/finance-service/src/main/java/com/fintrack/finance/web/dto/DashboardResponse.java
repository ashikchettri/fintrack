package com.fintrack.finance.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The "upload → instant dashboard" read model: everything the landing view needs
 * in one call. A computed projection over the caller's transactions (not an
 * entity), so it's a boundary record like every other DTO.
 */
public record DashboardResponse(
        String currency,
        // the selected month ("2026-07") or null for all-time; the snapshot
        // metrics (totals/byCategory/topMerchants/recent) respect it, while
        // byMonth stays the full trend for context
        String month,
        List<String> availableMonths,
        Totals totals,
        List<CategorySpend> byCategory,
        List<MonthlyFlow> byMonth,
        List<MerchantSpend> topMerchants,
        List<RecentTransaction> recent) {

    /** Headline figures across every transaction. */
    public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal net, long transactionCount) {
    }

    /** Spend per category with its share of total spend (0–1). */
    public record CategorySpend(String category, BigDecimal spent, double share) {
    }

    /** Income/expense/net for one calendar month ("2026-07"). */
    public record MonthlyFlow(String month, BigDecimal income, BigDecimal expenses, BigDecimal net) {
    }

    /** A merchant/description the member spends the most at. */
    public record MerchantSpend(String description, BigDecimal spent, long count) {
    }

    /** A recent transaction row for the activity feed. */
    public record RecentTransaction(
            UUID id, LocalDate date, String description, String category,
            BigDecimal amount, UUID accountId, String visibility) {
    }
}
