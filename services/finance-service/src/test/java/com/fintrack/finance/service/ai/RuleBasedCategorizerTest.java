package com.fintrack.finance.service.ai;

import com.fintrack.finance.domain.SpendingCategory;
import com.fintrack.finance.service.ai.TransactionCategorizer.CategorizationInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCategorizerTest {

    private final RuleBasedCategorizer categorizer = new RuleBasedCategorizer();

    @Test
    void mapsEachRowViaTheKeywordRules() {
        List<SpendingCategory> result = categorizer.categorize(List.of(
                new CategorizationInput("COLES 0234", "Food & Drink", new BigDecimal("-84.20")),
                new CategorizationInput("UBER TRIP", "Transportation", new BigDecimal("-24.00")),
                new CategorizationInput("MYSTERY MERCHANT", "Zorbing", new BigDecimal("-10.00"))));

        assertThat(result).containsExactly(
                SpendingCategory.GROCERIES, SpendingCategory.TRANSPORT, SpendingCategory.OTHER);
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertThat(categorizer.categorize(List.of())).isEmpty();
    }
}
