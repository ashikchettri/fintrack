package com.fintrack.insight.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A month's spending summary (ADR 012): the figures, an AI-or-template headline,
 * and a few insight bullets.
 */
public record MonthlySummaryResponse(
        String month,
        String currency,
        Totals totals,
        String headline,
        List<String> insights) {

    public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal net, long transactionCount) {
    }
}
