package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.BudgetFrequency;
import com.fintrack.finance.domain.BudgetLine;
import com.fintrack.finance.domain.BudgetSection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One budget line at the boundary. {@code amount}/{@code frequency} may be null
 * (a template row not yet filled). Monthly/annual are derived in the UI. Blank
 * rows (no name) are dropped on save.
 */
public record BudgetLineDto(
        @NotNull(message = "section is required")
        BudgetSection section,

        @Size(max = 60, message = "category must be at most 60 characters")
        String category,

        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        BudgetFrequency frequency,

        @DecimalMin(value = "0", message = "amount must be positive")
        @Digits(integer = 15, fraction = 4, message = "amount has at most 4 decimal places")
        BigDecimal amount
) {
    public static BudgetLineDto from(BudgetLine line) {
        return new BudgetLineDto(line.getSection(), line.getCategory(), line.getName(),
                line.getFrequency(), line.getAmount());
    }
}
