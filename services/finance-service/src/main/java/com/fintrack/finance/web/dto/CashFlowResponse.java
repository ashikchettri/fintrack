package com.fintrack.finance.web.dto;

import java.math.BigDecimal;

/**
 * The household's monthly cash-flow snapshot — the inputs to the affordability
 * question, kept transparent (each figure shown, not a black box).
 *
 * <p>{@code monthlySurplus = monthlyIncome − monthlyAvgSpending}. Spending is the
 * caller's recent average from their transactions (which already includes the
 * home-loan repayment if it was imported); {@code monthlyLoanRepayment} is shown
 * as context — how much of that spend is the loan.
 */
public record CashFlowResponse(
        String currency,
        BigDecimal monthlyIncome,
        BigDecimal monthlyLoanRepayment,
        BigDecimal monthlyAvgSpending,
        BigDecimal monthlySurplus,
        int monthsOfSpendingData) {
}
