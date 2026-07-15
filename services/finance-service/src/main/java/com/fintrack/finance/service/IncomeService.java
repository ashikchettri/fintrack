package com.fintrack.finance.service;

import com.fintrack.finance.domain.Income;
import com.fintrack.finance.repository.IncomeRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.HouseholdIncomeResponse;
import com.fintrack.finance.web.dto.HouseholdIncomeResponse.MemberIncome;
import com.fintrack.finance.web.dto.IncomeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Member incomes + the household total. A member edits their OWN income
 * (member-scoped); the summary sums across the household — both partners'
 * incomes feed the cash-flow calculation.
 */
@Service
public class IncomeService {

    private final IncomeRepository incomeRepository;

    public IncomeService(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Income> get(AuthenticatedMember caller) {
        return incomeRepository.findByHouseholdIdAndMemberId(caller.householdId(), caller.memberId());
    }

    @Transactional
    public Income save(AuthenticatedMember caller, IncomeRequest req) {
        Income income = incomeRepository
                .findByHouseholdIdAndMemberId(caller.householdId(), caller.memberId())
                .orElseGet(() -> new Income(caller.householdId(), caller.memberId()));

        income.setSalaryAmount(req.salaryAmount());
        income.setSalaryFrequency(req.salaryFrequency());
        income.setSuperRate(req.superRate());
        income.setBonusAnnual(req.bonusAnnual());
        income.setOtherIncomeAnnual(req.otherIncomeAnnual());
        income.setOtherIncomeNote(trim(req.otherIncomeNote()));
        income.setCurrency(req.currency() == null ? "AUD" : req.currency().toUpperCase(Locale.ROOT));
        income.setNotes(trim(req.notes()));
        income.touch();
        return incomeRepository.save(income);
    }

    @Transactional(readOnly = true)
    public HouseholdIncomeResponse householdSummary(AuthenticatedMember caller) {
        List<Income> incomes = incomeRepository.findByHouseholdId(caller.householdId());

        BigDecimal total = incomes.stream()
                .map(Income::annualIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MemberIncome> members = incomes.stream()
                .sorted(Comparator.comparing(Income::annualIncome).reversed())
                .map(i -> new MemberIncome(
                        i.getMemberId(),
                        i.getMemberId().equals(caller.memberId()),
                        i.annualIncome()))
                .toList();

        String currency = incomes.stream().map(Income::getCurrency).findFirst().orElse("AUD");
        return new HouseholdIncomeResponse(currency, total, members);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
