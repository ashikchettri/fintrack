package com.fintrack.finance.domain;

/**
 * Who in a household can see a transaction (ADR 001). Defaults to
 * {@link #PERSONAL} everywhere — sharing is always opt-in, so an imported bank
 * statement never becomes visible to other members by accident.
 *
 * <p>Persisted lowercase ({@code personal}/{@code shared}) via
 * {@link VisibilityConverter} to match the DB CHECK constraint.
 */
public enum Visibility {
    PERSONAL,
    SHARED;

    public String dbValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Visibility fromDb(String value) {
        return valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
