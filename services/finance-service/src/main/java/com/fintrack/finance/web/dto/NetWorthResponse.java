package com.fintrack.finance.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The household net-worth summary (ADR 014): totals and the breakdown, folding
 * the manual items together with the home loan (its balance as a liability, its
 * offset as an asset). {@code netWorth = totalAssets − totalLiabilities}.
 */
public record NetWorthResponse(
        String currency,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal netWorth,
        List<Line> assets,
        List<Line> liabilities) {

    /** One line on the balance sheet. {@code source} = MANUAL or HOME_LOAN (derived, read-only). */
    public record Line(String name, String category, BigDecimal value, String source) {
    }
}
