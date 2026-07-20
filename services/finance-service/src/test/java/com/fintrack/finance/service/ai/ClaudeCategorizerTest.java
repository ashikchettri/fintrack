package com.fintrack.finance.service.ai;

import com.fintrack.finance.domain.SpendingCategory;
import com.fintrack.finance.service.ai.TransactionCategorizer.CategorizationInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeCategorizerTest {

    private final AnthropicChatClient client = mock(AnthropicChatClient.class);
    private final RuleBasedCategorizer fallback = new RuleBasedCategorizer();
    private final ClaudeCategorizer categorizer =
            new ClaudeCategorizer(client, fallback, JsonMapper.builder().build(), 50);

    // opaque description with no keyword → OTHER for the rules, a case where AI adds value
    private static final CategorizationInput UNCLEAR =
            new CategorizationInput("OSKO PAYMENT 4471", "", new BigDecimal("-50.00"));
    private static final CategorizationInput CLEAR =
            new CategorizationInput("UBER TRIP", "Transportation", new BigDecimal("-24.00"));

    @Test
    void overlaysTheModelsCategoriesOnTopOfTheRuleBasedFloor() {
        when(client.complete(anyString(), anyString()))
                .thenReturn("""
                        [{"i": 0, "category": "GROCERIES"}, {"i": 1, "category": "TRANSPORT"}]""");

        List<SpendingCategory> result = categorizer.categorize(List.of(UNCLEAR, CLEAR));

        // the model lifted the unclear row off OTHER; the clear one agrees with the rules
        assertThat(result).containsExactly(SpendingCategory.GROCERIES, SpendingCategory.TRANSPORT);
    }

    @Test
    void toleratesProseAroundTheJsonArray() {
        when(client.complete(anyString(), anyString()))
                .thenReturn("Sure! Here you go:\n[{\"i\": 0, \"category\": \"GROCERIES\"}]\nHope that helps.");

        assertThat(categorizer.categorize(List.of(UNCLEAR)))
                .containsExactly(SpendingCategory.GROCERIES);
    }

    @Test
    void keepsRuleBasedWhenTheModelOmitsOrMislabelsARow() {
        // row 0 mislabelled (unknown enum) → falls back to rules (OTHER);
        // row 1 omitted entirely → keeps its rule-based TRANSPORT
        when(client.complete(anyString(), anyString()))
                .thenReturn("""
                        [{"i": 0, "category": "BANANAS"}]""");

        assertThat(categorizer.categorize(List.of(UNCLEAR, CLEAR)))
                .containsExactly(SpendingCategory.OTHER, SpendingCategory.TRANSPORT);
    }

    @Test
    void fallsBackEntirelyWhenTheModelCallThrows() {
        when(client.complete(anyString(), anyString())).thenThrow(new RuntimeException("503 overloaded"));

        // both rows keep their rule-based categories — the import never fails
        assertThat(categorizer.categorize(List.of(UNCLEAR, CLEAR)))
                .containsExactly(SpendingCategory.OTHER, SpendingCategory.TRANSPORT);
    }

    @Test
    void emptyInputSkipsTheModelEntirely() {
        assertThat(categorizer.categorize(List.of())).isEmpty();
    }
}
