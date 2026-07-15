package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.LoanOwnership;
import com.fintrack.finance.domain.RepaymentFrequency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The household's home-loan profile at the boundary. All fields but
 * {@code hasHomeLoan} are optional (the form can be partial); the server stays
 * lenient and derives cash flow from whatever's provided. Money bounds match
 * NUMERIC(19,4); interest is an annual percentage 0–100.
 */
public record HomeLoanRequest(
        // nullable so a partial form body doesn't fail JSON binding (a missing
        // primitive boolean is rejected); null is treated as false
        Boolean hasHomeLoan,

        @Size(max = 100, message = "lender must be at most 100 characters")
        String lender,

        @DecimalMin(value = "0", message = "loanAmount must be positive")
        @Digits(integer = 15, fraction = 4, message = "loanAmount has at most 4 decimal places")
        BigDecimal loanAmount,

        @DecimalMin(value = "0", message = "interestRate must be between 0 and 100")
        @DecimalMax(value = "100", message = "interestRate must be between 0 and 100")
        @Digits(integer = 3, fraction = 4, message = "interestRate has at most 4 decimal places")
        BigDecimal interestRate,

        RepaymentFrequency repaymentFrequency,

        @DecimalMin(value = "0", message = "repaymentAmount must be positive")
        @Digits(integer = 15, fraction = 4, message = "repaymentAmount has at most 4 decimal places")
        BigDecimal repaymentAmount,

        Boolean hasOffset,

        @DecimalMin(value = "0", message = "offsetBalance must be positive")
        @Digits(integer = 15, fraction = 4, message = "offsetBalance has at most 4 decimal places")
        BigDecimal offsetBalance,

        LoanOwnership ownership,

        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @Size(max = 500, message = "notes must be at most 500 characters")
        String notes
) {
}
