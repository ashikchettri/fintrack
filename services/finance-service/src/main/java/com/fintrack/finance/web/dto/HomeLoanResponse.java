package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.HomeLoan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The household's home-loan profile. When none is saved yet, the controller
 * returns a default with {@code hasHomeLoan = false} so the form always has a
 * shape to bind to.
 */
public record HomeLoanResponse(
        boolean hasHomeLoan,
        String lender,
        BigDecimal loanAmount,
        BigDecimal interestRate,
        String repaymentFrequency,
        BigDecimal repaymentAmount,
        boolean hasOffset,
        BigDecimal offsetBalance,
        String ownership,
        String currency,
        String notes,
        UUID updatedBy,
        Instant updatedAt) {

    public static HomeLoanResponse from(HomeLoan loan) {
        return new HomeLoanResponse(
                loan.isHasHomeLoan(),
                loan.getLender(),
                loan.getLoanAmount(),
                loan.getInterestRate(),
                loan.getRepaymentFrequency() == null ? null : loan.getRepaymentFrequency().name(),
                loan.getRepaymentAmount(),
                loan.isHasOffset(),
                loan.getOffsetBalance(),
                loan.getOwnership() == null ? null : loan.getOwnership().name(),
                loan.getCurrency(),
                loan.getNotes(),
                loan.getUpdatedBy(),
                loan.getUpdatedAt());
    }

    /** Shape returned before a household has saved anything. */
    public static HomeLoanResponse empty() {
        return new HomeLoanResponse(false, null, null, null, null, null,
                false, null, null, "AUD", null, null, null);
    }
}
