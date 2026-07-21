package com.fintrack.insight.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Turns a month's aggregate figures into a short narrative (ADR 012). The seam
 * behind which insight-service chooses a deterministic template or Claude.
 */
public interface SummaryGenerator {

    MonthlySummary summarize(SummaryInput input);

    /** Only aggregate figures — never individual line items (ADR 012 privacy). */
    record SummaryInput(
            String month,
            String currency,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal net,
            long transactionCount,
            List<CategoryShare> topCategories) {
    }

    record CategoryShare(String category, BigDecimal spent, double share) {
    }

    /** A headline line plus a few insight bullets. */
    record MonthlySummary(String headline, List<String> insights) {
    }
}
