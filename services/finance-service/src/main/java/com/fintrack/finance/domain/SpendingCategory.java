package com.fintrack.finance.domain;

/**
 * The canonical spending vocabulary (ADR 008): the single set of categories that
 * both budget lines and transactions map to, so plan and actuals can be compared
 * per category. The expense values are exactly the budget's expense groups;
 * {@link #OTHER} is the catch-all. This is the fixed target the Phase 4 AI emits.
 */
public enum SpendingCategory {
    INCOME("Income"),
    HOUSING("Housing"),
    UTILITIES("Utilities & Communications"),
    GROCERIES("Groceries & Food"),
    TRANSPORT("Transport"),
    KIDS_FAMILY("Kids & Family"),
    HEALTH("Health & Wellbeing"),
    INSURANCE_FINANCIAL("Insurance & Financial"),
    SUBSCRIPTIONS("Subscriptions & Entertainment"),
    PERSONAL("Personal & Miscellaneous"),
    SAVINGS("Savings"),
    OTHER("Other");

    private final String label;

    SpendingCategory(String label) {
        this.label = label;
    }

    /** Human-readable label — matches the budget group headings. */
    public String label() {
        return label;
    }
}
