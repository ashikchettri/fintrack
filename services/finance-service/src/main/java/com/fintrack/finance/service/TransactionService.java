package com.fintrack.finance.service;

import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
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

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<Transaction> list(AuthenticatedMember caller) {
        return transactionRepository.findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
                caller.householdId(), caller.memberId());
    }
}
