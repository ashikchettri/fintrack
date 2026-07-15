package com.fintrack.finance.web.dto;

import com.fintrack.finance.domain.Income;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A member's own income. {@code annualIncome} is the derived spendable figure
 * (salary annualized + bonus + other; super excluded). An {@link #empty()}
 * shape is returned before anything is saved so the form always binds.
 */
public record IncomeResponse(
        BigDecimal salaryAmount,
        String salaryFrequency,
        BigDecimal superRate,
        BigDecimal bonusAnnual,
        BigDecimal otherIncomeAnnual,
        String otherIncomeNote,
        BigDecimal annualIncome,
        String currency,
        String notes,
        Instant updatedAt) {

    public static IncomeResponse from(Income income) {
        return new IncomeResponse(
                income.getSalaryAmount(),
                income.getSalaryFrequency() == null ? null : income.getSalaryFrequency().name(),
                income.getSuperRate(),
                income.getBonusAnnual(),
                income.getOtherIncomeAnnual(),
                income.getOtherIncomeNote(),
                income.annualIncome(),
                income.getCurrency(),
                income.getNotes(),
                income.getUpdatedAt());
    }

    public static IncomeResponse empty() {
        return new IncomeResponse(null, null, null, null, null, null,
                BigDecimal.ZERO, "AUD", null, null);
    }
}
