package com.fintrack.finance.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The household's combined income — every member's annual income plus the total.
 * Member names are resolved by the UI from the household roster (finance-service
 * only knows member ids).
 */
public record HouseholdIncomeResponse(
        String currency,
        BigDecimal annualTotal,
        List<MemberIncome> members) {

    public record MemberIncome(UUID memberId, boolean isYou, BigDecimal annualIncome) {
    }
}
