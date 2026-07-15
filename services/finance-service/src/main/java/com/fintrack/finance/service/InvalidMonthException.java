package com.fintrack.finance.service;

/** The {@code month} query param wasn't a valid {@code YYYY-MM} value. */
public class InvalidMonthException extends RuntimeException {
    public InvalidMonthException(String value) {
        super("month must be in YYYY-MM format, was: " + value);
    }
}
