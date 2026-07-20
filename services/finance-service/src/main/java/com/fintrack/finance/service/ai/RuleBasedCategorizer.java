package com.fintrack.finance.service.ai;

import com.fintrack.finance.domain.SpendingCategory;
import com.fintrack.finance.service.CategoryMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The always-present default (ADR 009): the keyword rules from ADR 008. It's the
 * floor the AI categorizer falls back to, and the sole categorizer when AI is
 * off — so imports work with no API key.
 */
@Component
public class RuleBasedCategorizer implements TransactionCategorizer {

    @Override
    public List<SpendingCategory> categorize(List<CategorizationInput> inputs) {
        return inputs.stream()
                .map(i -> CategoryMapper.fromBankCategory(i.bankCategory(), i.description()))
                .toList();
    }
}
