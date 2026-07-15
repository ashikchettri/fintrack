package com.fintrack.finance.service;

import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.DashboardResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the dashboard read model for the caller. Aggregation runs in memory:
 * a household's own transaction volume is modest, and one scoped fetch keeps the
 * whole computation in a single readable place (and trivially testable). If a
 * member ever accumulates enough rows to matter, this becomes SQL rollups —
 * behind the same method.
 */
@Service
public class DashboardService {

    private static final int TOP_MERCHANTS = 5;
    private static final int RECENT_LIMIT = 10;
    private static final String UNCATEGORIZED = "Uncategorized";

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(AuthenticatedMember caller, YearMonth month) {
        // already sorted most-recent first by the repository
        List<Transaction> txns = transactionRepository
                .findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
                        caller.householdId(), caller.memberId());

        // snapshot metrics respect the selected month; the trend stays all-time
        List<Transaction> scoped = month == null
                ? txns
                : txns.stream().filter(t -> YearMonth.from(t.getTxnDate()).equals(month)).toList();

        return new DashboardResponse(
                dominantCurrency(txns),
                month == null ? null : month.toString(),
                availableMonths(txns),
                totals(scoped),
                byCategory(scoped),
                byMonth(txns),
                topMerchants(scoped),
                recent(scoped));
    }

    /** Every month with activity, newest first — populates the month selector. */
    private List<String> availableMonths(List<Transaction> txns) {
        return txns.stream()
                .map(t -> YearMonth.from(t.getTxnDate()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(YearMonth::toString)
                .toList();
    }

    private String dominantCurrency(List<Transaction> txns) {
        return txns.stream()
                .collect(Collectors.groupingBy(Transaction::getCurrency, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private DashboardResponse.Totals totals(List<Transaction> txns) {
        BigDecimal income = sum(txns.stream().filter(this::isIncome));
        BigDecimal expenses = sum(txns.stream().filter(this::isExpense)).abs();
        return new DashboardResponse.Totals(income, expenses, income.subtract(expenses), txns.size());
    }

    private List<DashboardResponse.CategorySpend> byCategory(List<Transaction> txns) {
        Map<String, BigDecimal> spendByCategory = txns.stream()
                .filter(this::isExpense)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() == null ? UNCATEGORIZED : t.getCategory(),
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));

        BigDecimal totalSpend = spendByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return spendByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new DashboardResponse.CategorySpend(e.getKey(), e.getValue(), share(e.getValue(), totalSpend)))
                .toList();
    }

    private List<DashboardResponse.MonthlyFlow> byMonth(List<Transaction> txns) {
        Map<YearMonth, List<Transaction>> byMonth = txns.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getTxnDate())));
        return byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    BigDecimal income = sum(e.getValue().stream().filter(this::isIncome));
                    BigDecimal expenses = sum(e.getValue().stream().filter(this::isExpense)).abs();
                    return new DashboardResponse.MonthlyFlow(
                            e.getKey().toString(), income, expenses, income.subtract(expenses));
                })
                .toList();
    }

    private List<DashboardResponse.MerchantSpend> topMerchants(List<Transaction> txns) {
        Map<String, List<Transaction>> byMerchant = txns.stream()
                .filter(this::isExpense)
                .collect(Collectors.groupingBy(Transaction::getDescription));
        return byMerchant.entrySet().stream()
                .map(e -> new DashboardResponse.MerchantSpend(
                        e.getKey(),
                        sum(e.getValue().stream()).abs(),
                        e.getValue().size()))
                .sorted(Comparator.comparing(DashboardResponse.MerchantSpend::spent).reversed())
                .limit(TOP_MERCHANTS)
                .toList();
    }

    private List<DashboardResponse.RecentTransaction> recent(List<Transaction> txns) {
        return txns.stream()
                .limit(RECENT_LIMIT)
                .map(t -> new DashboardResponse.RecentTransaction(
                        t.getId(), t.getTxnDate(), t.getDescription(), t.getCategory(),
                        t.getAmount(), t.getAccountId(), t.getVisibility().dbValue()))
                .toList();
    }

    private boolean isIncome(Transaction t) {
        return t.getAmount().signum() >= 0;
    }

    private boolean isExpense(Transaction t) {
        return t.getAmount().signum() < 0;
    }

    private BigDecimal sum(java.util.stream.Stream<Transaction> stream) {
        return stream.map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double share(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return 0.0;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP).doubleValue();
    }
}
