package com.fintrack.finance.domain;

/** How often a budget line is paid — with the multiplier to annualize it. */
public enum BudgetFrequency {
    WEEKLY(52),
    FORTNIGHTLY(26),
    MONTHLY(12),
    QUARTERLY(4),
    ANNUALLY(1);

    private final int perYear;

    BudgetFrequency(int perYear) {
        this.perYear = perYear;
    }

    public int perYear() {
        return perYear;
    }
}
