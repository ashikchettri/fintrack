package com.fintrack.finance.web;

import com.fintrack.finance.service.InvalidMonthException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Parses the optional {@code ?month=YYYY-MM} query param shared by the dashboard + household views. */
final class MonthParam {

    private MonthParam() {
    }

    /** {@code null}/blank → all-time; a valid {@code YYYY-MM} → that month; anything else → 400. */
    static YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeParseException e) {
            throw new InvalidMonthException(month);
        }
    }
}
