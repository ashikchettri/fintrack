package com.fintrack.insight.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The subset of finance-service's dashboard we summarize (ADR 012). Records +
 * {@code @JsonIgnoreProperties} so it's tolerant of the fields we don't use and
 * Jackson-version agnostic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceDashboard(
        String currency,
        String month,
        List<String> availableMonths,
        Totals totals,
        List<CategorySpend> byCategory,
        List<MerchantSpend> topMerchants) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal net, long transactionCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategorySpend(String category, BigDecimal spent, double share) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MerchantSpend(String description, BigDecimal spent, long count) {
    }
}
