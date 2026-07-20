package com.fintrack.finance.service;

import com.fintrack.finance.domain.SpendingCategory;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.ai.TransactionCategorizer;
import com.fintrack.finance.service.ai.TransactionCategorizer.CategorizationInput;
import com.fintrack.finance.web.dto.RecategorizeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads a member's transactions. Like every finance query, scoped to the
 * caller's household + member from the verified JWT — never request input.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionCategorizer categorizer;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionCategorizer categorizer) {
        this.transactionRepository = transactionRepository;
        this.categorizer = categorizer;
    }

    @Transactional(readOnly = true)
    public List<Transaction> list(AuthenticatedMember caller) {
        return transactionRepository.findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
                caller.householdId(), caller.memberId());
    }

    /**
     * Re-run the active categorizer over all of the caller's transactions and
     * persist any changed canonical categories (ADR 009). Lets already-imported
     * rows benefit from AI (or improved rules) without re-uploading. Batched
     * inside the categorizer; scoped to the caller's own rows.
     */
    @Transactional
    public RecategorizeResponse recategorize(AuthenticatedMember caller) {
        List<Transaction> txns = transactionRepository
                .findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
                        caller.householdId(), caller.memberId());
        if (txns.isEmpty()) {
            return new RecategorizeResponse(0, 0);
        }

        List<SpendingCategory> categories = categorizer.categorize(txns.stream()
                .map(t -> new CategorizationInput(t.getDescription(), t.getCategory(), t.getAmount()))
                .toList());

        int changed = 0;
        for (int i = 0; i < txns.size(); i++) {
            String next = categories.get(i).name();
            if (!next.equals(txns.get(i).getCanonicalCategory())) {
                txns.get(i).setCanonicalCategory(next);
                changed++;
            }
        }
        transactionRepository.saveAll(txns);
        return new RecategorizeResponse(txns.size(), changed);
    }
}
