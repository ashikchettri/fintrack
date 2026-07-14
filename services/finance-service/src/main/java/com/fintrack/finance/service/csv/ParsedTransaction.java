package com.fintrack.finance.service.csv;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One CSV row after parsing + normalization, before it becomes a persisted
 * {@link com.fintrack.finance.domain.Transaction}. Currency is not here — the
 * statement doesn't carry it, so it's supplied by the import request.
 *
 * @param amount signed: spend (debit) is negative, income (credit) positive
 */
public record ParsedTransaction(
        int rowNumber,
        LocalDate date,
        String accountName,
        String description,
        String category,
        String subcategory,
        String tags,
        String notes,
        BigDecimal amount,
        String originalDescription) {
}
