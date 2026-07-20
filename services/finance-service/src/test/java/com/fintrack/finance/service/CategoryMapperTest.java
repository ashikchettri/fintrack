package com.fintrack.finance.service;

import com.fintrack.finance.domain.SpendingCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    @Test
    void mapsBudgetGroupHeadingsToTheirCanonicalCategory() {
        assertThat(CategoryMapper.fromBudgetGroup("Groceries & Food")).isEqualTo(SpendingCategory.GROCERIES);
        assertThat(CategoryMapper.fromBudgetGroup("Housing")).isEqualTo(SpendingCategory.HOUSING);
        assertThat(CategoryMapper.fromBudgetGroup("Utilities & Communications")).isEqualTo(SpendingCategory.UTILITIES);
        // case / whitespace insensitive, and a common reworded alias
        assertThat(CategoryMapper.fromBudgetGroup("  transport ")).isEqualTo(SpendingCategory.TRANSPORT);
        assertThat(CategoryMapper.fromBudgetGroup("Entertainment")).isEqualTo(SpendingCategory.SUBSCRIPTIONS);
    }

    @Test
    void unrecognisedOrMissingBudgetGroupIsOther() {
        assertThat(CategoryMapper.fromBudgetGroup("Wormholes")).isEqualTo(SpendingCategory.OTHER);
        assertThat(CategoryMapper.fromBudgetGroup(null)).isEqualTo(SpendingCategory.OTHER);
        assertThat(CategoryMapper.fromBudgetGroup("  ")).isEqualTo(SpendingCategory.OTHER);
    }

    @Test
    void mapsBankCategoriesToCanonicalViaKeywords() {
        // the exact mismatch this ADR exists to fix: bank "Food & Drink" → GROCERIES
        assertThat(CategoryMapper.fromBankCategory("Food & Drink", "COLES 0234")).isEqualTo(SpendingCategory.GROCERIES);
        assertThat(CategoryMapper.fromBankCategory("Transportation", "UBER TRIP")).isEqualTo(SpendingCategory.TRANSPORT);
        assertThat(CategoryMapper.fromBankCategory("Bills & Utilities", "AGL ENERGY")).isEqualTo(SpendingCategory.UTILITIES);
        assertThat(CategoryMapper.fromBankCategory("Entertainment", "NETFLIX.COM")).isEqualTo(SpendingCategory.SUBSCRIPTIONS);
    }

    @Test
    void fallsBackToTheDescriptionWhenTheCategoryIsUnhelpful() {
        // no useful category, but the description carries the signal
        assertThat(CategoryMapper.fromBankCategory(null, "Woolworths Metro")).isEqualTo(SpendingCategory.GROCERIES);
        assertThat(CategoryMapper.fromBankCategory("", "Monthly salary")).isEqualTo(SpendingCategory.INCOME);
    }

    @Test
    void unknownBankCategoryIsOther() {
        assertThat(CategoryMapper.fromBankCategory("Zorbing", "MYSTERY MERCHANT")).isEqualTo(SpendingCategory.OTHER);
        assertThat(CategoryMapper.fromBankCategory(null, null)).isEqualTo(SpendingCategory.OTHER);
    }

    @Test
    void resolvePrefersTheStoredCanonicalButFallsBackToLiveDerivation() {
        // stored canonical wins
        assertThat(CategoryMapper.resolve("GROCERIES", "Transportation", "UBER"))
                .isEqualTo(SpendingCategory.GROCERIES);
        // null stored → derive from raw bank fields (pre-V7 rows)
        assertThat(CategoryMapper.resolve(null, "Transportation", "UBER"))
                .isEqualTo(SpendingCategory.TRANSPORT);
        // stale/invalid stored value → derive
        assertThat(CategoryMapper.resolve("NOT_A_CATEGORY", "Food & Drink", "ALDI"))
                .isEqualTo(SpendingCategory.GROCERIES);
    }
}
