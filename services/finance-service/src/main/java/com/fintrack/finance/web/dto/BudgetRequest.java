package com.fintrack.finance.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Replace-all save of the household budget. */
public record BudgetRequest(
        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @Valid
        List<BudgetLineDto> lines
) {
}
