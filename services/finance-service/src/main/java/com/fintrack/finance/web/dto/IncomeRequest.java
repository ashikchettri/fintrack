package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.PayFrequency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A member's income at the boundary — all optional; the server annualizes
 * whatever's given. Money bounds match NUMERIC(19,4); super is an annual %.
 */
public record IncomeRequest(
        @DecimalMin(value = "0", message = "salaryAmount must be positive")
        @Digits(integer = 15, fraction = 4, message = "salaryAmount has at most 4 decimal places")
        BigDecimal salaryAmount,

        PayFrequency salaryFrequency,

        @DecimalMin(value = "0", message = "superRate must be between 0 and 100")
        @DecimalMax(value = "100", message = "superRate must be between 0 and 100")
        @Digits(integer = 3, fraction = 4, message = "superRate has at most 4 decimal places")
        BigDecimal superRate,

        @DecimalMin(value = "0", message = "bonusAnnual must be positive")
        @Digits(integer = 15, fraction = 4, message = "bonusAnnual has at most 4 decimal places")
        BigDecimal bonusAnnual,

        @DecimalMin(value = "0", message = "otherIncomeAnnual must be positive")
        @Digits(integer = 15, fraction = 4, message = "otherIncomeAnnual has at most 4 decimal places")
        BigDecimal otherIncomeAnnual,

        @Size(max = 200, message = "otherIncomeNote must be at most 200 characters")
        String otherIncomeNote,

        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @Size(max = 500, message = "notes must be at most 500 characters")
        String notes
) {
}
