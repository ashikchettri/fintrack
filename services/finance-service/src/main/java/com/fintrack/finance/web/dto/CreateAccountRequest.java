package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.AccountType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotNull(message = "type is required")
        AccountType type,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        // money bounds match NUMERIC(19,4): 15 integer digits, 4 fraction
        @NotNull(message = "openingBalance is required")
        @DecimalMin(value = "-999999999999999.9999", message = "openingBalance out of range")
        @DecimalMax(value = "999999999999999.9999", message = "openingBalance out of range")
        @Digits(integer = 15, fraction = 4, message = "openingBalance has at most 4 decimal places")
        BigDecimal openingBalance
) {
}
