package com.fintrack.finance.service.ai;

import com.fintrack.finance.domain.SpendingCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps a batch of transactions onto the canonical {@link SpendingCategory}
 * vocabulary (ADR 008/009). The seam behind which the import path chooses
 * between rule-based and AI categorization. Implementations never throw — they
 * degrade to a sensible category rather than fail an import.
 */
public interface TransactionCategorizer {

    /** One canonical category per input, in the same order. */
    List<SpendingCategory> categorize(List<CategorizationInput> inputs);

    /** The signals a categorizer is allowed to see — description, the bank's own
     *  category label, and the amount (only its sign ever leaves the boundary). */
    record CategorizationInput(String description, String bankCategory, BigDecimal amount) {
    }
}
