package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction at the API boundary — a record, never the JPA entity. {@code
 * amount} is signed (spend negative, income positive), as stored.
 */
public record TransactionResponse(
        UUID id,
        UUID accountId,
        LocalDate date,
        String description,
        String category,
        String subcategory,
        BigDecimal amount,
        String currency,
        String tags,
        String notes,
        String visibility,
        String source) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAccountId(),
                t.getTxnDate(),
                t.getDescription(),
                t.getCategory(),
                t.getSubcategory(),
                t.getAmount(),
                t.getCurrency(),
                t.getTags(),
                t.getNotes(),
                t.getVisibility().dbValue(),
                t.getSource().name());
    }
}
