package com.fintrack.finance.domain;

import java.math.BigDecimal;

/** How often a salary is paid — with the multiplier to annualize it. */
public enum PayFrequency {
    WEEKLY(52),
    FORTNIGHTLY(26),
    MONTHLY(12),
    ANNUALLY(1);

    private final int perYear;

    PayFrequency(int perYear) {
        this.perYear = perYear;
    }

    /** Multiply a per-period amount by this to get the annual figure. */
    public BigDecimal annualMultiplier() {
        return BigDecimal.valueOf(perYear);
    }
}
