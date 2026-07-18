package com.fintrack.finance.domain;

/** How often a loan is repaid — used to normalize repayments to a monthly cash flow. */
public enum RepaymentFrequency {
    WEEKLY(52),
    FORTNIGHTLY(26),
    MONTHLY(12);

    private final int perYear;

    RepaymentFrequency(int perYear) {
        this.perYear = perYear;
    }

    /** Number of repayments per year — multiply a per-period repayment by this to annualize. */
    public int perYear() {
        return perYear;
    }
}
