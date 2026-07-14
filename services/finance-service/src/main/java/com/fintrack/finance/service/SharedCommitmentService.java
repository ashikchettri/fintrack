package com.fintrack.finance.service;

import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.SharedHouseholdView;
import com.fintrack.finance.web.dto.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared commitments (ADR 006): the differentiator a bank can't offer. A member
 * marks their own costs as shared; the household then sees only those shared
 * items, who covered what, and a suggested settlement — never anyone's personal
 * spending.
 *
 * <p>The privacy boundary is the query: {@code setVisibility} is member-scoped
 * (you can only expose your own rows) and {@link #sharedView} reads by
 * {@code household_id AND visibility = shared}, so personal rows are structurally
 * unreachable across members.
 */
@Service
public class SharedCommitmentService {

    private static final String UNCATEGORIZED = "Uncategorized";

    private final TransactionRepository transactionRepository;

    public SharedCommitmentService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /** Mark/unmark one of the caller's OWN transactions as shared. */
    @Transactional
    public Transaction setVisibility(AuthenticatedMember caller, UUID id, Visibility visibility) {
        Transaction txn = transactionRepository
                .findByIdAndHouseholdIdAndMemberId(id, caller.householdId(), caller.memberId())
                .orElseThrow(() -> new TransactionNotFoundException(id));
        txn.setVisibility(visibility);
        return transactionRepository.save(txn);
    }

    @Transactional(readOnly = true)
    public SharedHouseholdView sharedView(AuthenticatedMember caller) {
        // only the household's SHARED rows are ever loaded — personal rows never
        // match this query (the privacy boundary)
        List<Transaction> shared = transactionRepository
                .findByHouseholdIdAndVisibilityOrderByTxnDateDescCreatedAtDesc(
                        caller.householdId(), Visibility.SHARED);
        // a shared commitment is a shared cost — spend, not income
        List<Transaction> commitments = shared.stream()
                .filter(t -> t.getAmount().signum() < 0)
                .toList();

        BigDecimal total = commitments.stream()
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, BigDecimal> coveredByMember = commitments.stream()
                .collect(Collectors.groupingBy(Transaction::getMemberId,
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));

        int members = coveredByMember.size();
        BigDecimal fairShare = members == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(members), 2, RoundingMode.HALF_UP);

        return new SharedHouseholdView(
                dominantCurrency(commitments),
                total,
                members,
                fairShare,
                settlement(caller, coveredByMember, fairShare),
                contributions(caller, coveredByMember),
                byCategory(commitments),
                commitments.stream().map(TransactionResponse::from).toList());
    }

    private SharedHouseholdView.Settlement settlement(AuthenticatedMember caller,
                                                      Map<UUID, BigDecimal> covered, BigDecimal fairShare) {
        BigDecimal yours = covered.getOrDefault(caller.memberId(), BigDecimal.ZERO);
        BigDecimal balance = yours.subtract(fairShare);
        String status = switch (balance.signum()) {
            case 1 -> "owed";   // you covered more than your share → you're owed
            case -1 -> "owes";  // you covered less → you owe
            default -> "settled";
        };
        return new SharedHouseholdView.Settlement(yours, fairShare, balance, status, balance.abs());
    }

    private List<SharedHouseholdView.Contribution> contributions(AuthenticatedMember caller,
                                                                 Map<UUID, BigDecimal> covered) {
        return covered.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .map(e -> new SharedHouseholdView.Contribution(
                        e.getKey(), e.getValue(), e.getKey().equals(caller.memberId())))
                .toList();
    }

    private List<SharedHouseholdView.CategoryShare> byCategory(List<Transaction> commitments) {
        return commitments.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() == null ? UNCATEGORIZED : t.getCategory(),
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new SharedHouseholdView.CategoryShare(e.getKey(), e.getValue()))
                .toList();
    }

    private String dominantCurrency(List<Transaction> commitments) {
        return commitments.stream()
                .collect(Collectors.groupingBy(Transaction::getCurrency, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
