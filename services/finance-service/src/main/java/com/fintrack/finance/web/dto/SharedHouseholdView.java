package com.fintrack.finance.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The private household view of shared commitments (ADR 006): only the shared
 * items and the agreed totals — never anyone's personal spending. This is the
 * payload behind "coordinate shared money without giving up private money".
 */
public record SharedHouseholdView(
        String currency,
        BigDecimal totalShared,
        int memberCount,
        BigDecimal fairShare,
        Settlement settlement,
        List<Contribution> contributions,
        List<CategoryShare> byCategory,
        List<TransactionResponse> transactions) {

    /** From the caller's perspective: what they've covered vs their fair share. */
    public record Settlement(
            BigDecimal yourContribution,
            BigDecimal fairShare,
            BigDecimal balance,   // covered − fair share; >0 you're owed, <0 you owe
            String status,        // "owed" | "owes" | "settled"
            BigDecimal amount) {  // abs(balance) — the suggested settlement
    }

    /** How much of the shared total one member has covered. */
    public record Contribution(UUID memberId, BigDecimal covered, boolean isYou) {
    }

    /** Shared spend grouped by category. */
    public record CategoryShare(String category, BigDecimal amount) {
    }
}
