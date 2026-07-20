package com.fintrack.finance.service;

import com.fintrack.finance.domain.BudgetLine;
import com.fintrack.finance.domain.BudgetSection;
import com.fintrack.finance.domain.SpendingCategory;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.repository.BudgetLineRepository;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.OverviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The dashboard rollup: the household's monthly budget (plan) vs the caller's
 * most recent month of transactions (reality). Ties the budget, income and bank
 * feed together so the dashboard is a financial position, not just a statement.
 */
@Service
public class OverviewService {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final BudgetLineRepository budgetLineRepository;
    private final TransactionRepository transactionRepository;

    public OverviewService(BudgetLineRepository budgetLineRepository,
                           TransactionRepository transactionRepository) {
        this.budgetLineRepository = budgetLineRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(AuthenticatedMember caller) {
        List<BudgetLine> budget = budgetLineRepository.findByHouseholdIdOrderBySortOrder(caller.householdId());
        BigDecimal plannedIncome = monthlyTotal(budget, BudgetSection.INCOME);
        BigDecimal plannedExpenses = monthlyTotal(budget, BudgetSection.EXPENSE);
        BigDecimal plannedSavings = monthlyTotal(budget, BudgetSection.SAVING);
        var planned = new OverviewResponse.Planned(
                plannedIncome, plannedExpenses, plannedSavings,
                plannedIncome.subtract(plannedExpenses).subtract(plannedSavings));

        List<Transaction> txns = transactionRepository
                .findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
                        caller.householdId(), caller.memberId());
        YearMonth latest = txns.stream()
                .map(t -> YearMonth.from(t.getTxnDate()))
                .max(YearMonth::compareTo)
                .orElse(null);

        BigDecimal actualIncome = BigDecimal.ZERO;
        BigDecimal actualExpenses = BigDecimal.ZERO;
        Map<SpendingCategory, BigDecimal> actualByCategory = new EnumMap<>(SpendingCategory.class);
        if (latest != null) {
            for (Transaction t : txns) {
                if (!YearMonth.from(t.getTxnDate()).equals(latest)) {
                    continue;
                }
                if (t.getAmount().signum() >= 0) {
                    actualIncome = actualIncome.add(t.getAmount());
                } else {
                    actualExpenses = actualExpenses.add(t.getAmount().abs());
                    SpendingCategory category = CategoryMapper.resolve(
                            t.getCanonicalCategory(), t.getCategory(), t.getDescription());
                    actualByCategory.merge(category, t.getAmount().abs(), BigDecimal::add);
                }
            }
        }

        String currency = budget.isEmpty() ? "AUD" : budget.get(0).getCurrency();
        return new OverviewResponse(
                currency,
                !budget.isEmpty(),
                latest == null ? null : latest.toString(),
                planned,
                new OverviewResponse.Actual(actualIncome, actualExpenses),
                byCategory(plannedByCategory(budget), actualByCategory));
    }

    /** Monthly planned spend per canonical category, from the EXPENSE budget lines. */
    private Map<SpendingCategory, BigDecimal> plannedByCategory(List<BudgetLine> budget) {
        Map<SpendingCategory, BigDecimal> planned = new EnumMap<>(SpendingCategory.class);
        for (BudgetLine line : budget) {
            if (line.getSection() != BudgetSection.EXPENSE) {
                continue;
            }
            SpendingCategory category = CategoryMapper.fromBudgetGroup(line.getCategory());
            planned.merge(category, monthlyOf(line), BigDecimal::add);
        }
        return planned;
    }

    /**
     * One comparison row per canonical category present in the plan or the actuals,
     * ordered by planned spend then actual — the biggest budget lines first.
     */
    private List<OverviewResponse.CategoryComparison> byCategory(
            Map<SpendingCategory, BigDecimal> planned,
            Map<SpendingCategory, BigDecimal> actual) {
        var categories = new java.util.TreeSet<SpendingCategory>();
        categories.addAll(planned.keySet());
        categories.addAll(actual.keySet());

        List<OverviewResponse.CategoryComparison> rows = new ArrayList<>();
        for (SpendingCategory category : categories) {
            rows.add(new OverviewResponse.CategoryComparison(
                    category.label(),
                    planned.getOrDefault(category, BigDecimal.ZERO),
                    actual.getOrDefault(category, BigDecimal.ZERO)));
        }
        rows.sort(Comparator
                .comparing(OverviewResponse.CategoryComparison::planned)
                .thenComparing(OverviewResponse.CategoryComparison::actual)
                .reversed());
        return rows;
    }

    private BigDecimal monthlyTotal(List<BudgetLine> lines, BudgetSection section) {
        return lines.stream()
                .filter(l -> l.getSection() == section)
                .map(this::monthlyOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal monthlyOf(BudgetLine line) {
        if (line.getAmount() == null || line.getFrequency() == null) {
            return BigDecimal.ZERO;
        }
        return line.getAmount()
                .multiply(BigDecimal.valueOf(line.getFrequency().perYear()))
                .divide(TWELVE, 2, RoundingMode.HALF_UP);
    }
}
