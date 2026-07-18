package com.fintrack.finance.service;

import com.fintrack.finance.domain.HomeLoan;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.repository.HomeLoanRepository;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.CashFlowResponse;
import com.fintrack.finance.web.dto.HouseholdIncomeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The household's monthly cash flow — income vs spending — the inputs to the
 * affordability question. Figures are normalized to monthly and kept explicit so
 * the UI can show (and let the user adjust) every assumption rather than hiding
 * it behind a single number.
 */
@Service
public class CashFlowService {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final IncomeService incomeService;
    private final HomeLoanRepository homeLoanRepository;
    private final TransactionRepository transactionRepository;

    public CashFlowService(IncomeService incomeService,
                           HomeLoanRepository homeLoanRepository,
                           TransactionRepository transactionRepository) {
        this.incomeService = incomeService;
        this.homeLoanRepository = homeLoanRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public CashFlowResponse summary(AuthenticatedMember caller) {
        HouseholdIncomeResponse income = incomeService.householdSummary(caller);
        BigDecimal monthlyIncome = income.annualTotal().divide(TWELVE, 2, RoundingMode.HALF_UP);

        BigDecimal monthlyLoan = monthlyLoanRepayment(caller);

        // caller's own recent spending, averaged per month
        List<Transaction> txns = transactionRepository
                .findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
                        caller.householdId(), caller.memberId());
        Map<YearMonth, BigDecimal> spendByMonth = txns.stream()
                .filter(t -> t.getAmount().signum() < 0)
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getTxnDate()),
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));
        int months = spendByMonth.size();
        BigDecimal avgSpending = months == 0
                ? BigDecimal.ZERO
                : spendByMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        BigDecimal surplus = monthlyIncome.subtract(avgSpending);
        return new CashFlowResponse(income.currency(), monthlyIncome, monthlyLoan, avgSpending, surplus, months);
    }

    private BigDecimal monthlyLoanRepayment(AuthenticatedMember caller) {
        return homeLoanRepository.findByHouseholdId(caller.householdId())
                .filter(HomeLoan::isHasHomeLoan)
                .map(loan -> {
                    if (loan.getRepaymentAmount() == null || loan.getRepaymentFrequency() == null) {
                        return BigDecimal.ZERO;
                    }
                    return loan.getRepaymentAmount()
                            .multiply(BigDecimal.valueOf(loan.getRepaymentFrequency().perYear()))
                            .divide(TWELVE, 2, RoundingMode.HALF_UP);
                })
                .orElse(BigDecimal.ZERO);
    }
}
