package com.fintrack.finance.service;

import com.fintrack.finance.domain.HomeLoan;
import com.fintrack.finance.repository.HomeLoanRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.HomeLoanRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * The household's home-loan profile. Scoped by household (jointly held), so any
 * member can read or edit it — unlike transactions, which are member-scoped.
 * One profile per household, upserted.
 */
@Service
public class HomeLoanService {

    private final HomeLoanRepository homeLoanRepository;

    public HomeLoanService(HomeLoanRepository homeLoanRepository) {
        this.homeLoanRepository = homeLoanRepository;
    }

    @Transactional(readOnly = true)
    public Optional<HomeLoan> get(AuthenticatedMember caller) {
        return homeLoanRepository.findByHouseholdId(caller.householdId());
    }

    @Transactional
    public HomeLoan save(AuthenticatedMember caller, HomeLoanRequest req) {
        HomeLoan loan = homeLoanRepository.findByHouseholdId(caller.householdId())
                .orElseGet(() -> new HomeLoan(caller.householdId()));

        boolean hasLoan = Boolean.TRUE.equals(req.hasHomeLoan());
        boolean hasOffset = Boolean.TRUE.equals(req.hasOffset());
        loan.setHasHomeLoan(hasLoan);
        if (hasLoan) {
            loan.setLender(trim(req.lender()));
            loan.setLoanAmount(req.loanAmount());
            loan.setInterestRate(req.interestRate());
            loan.setRepaymentFrequency(req.repaymentFrequency());
            loan.setRepaymentAmount(req.repaymentAmount());
            loan.setHasOffset(hasOffset);
            // drop a stale offset balance if the offset flag is now off
            loan.setOffsetBalance(hasOffset ? req.offsetBalance() : null);
            loan.setOwnership(req.ownership());
            loan.setNotes(trim(req.notes()));
            loan.setCurrency(req.currency() == null ? "AUD" : req.currency().toUpperCase(Locale.ROOT));
        } else {
            // "no home loan" — clear the detail fields so nothing stale lingers
            clearDetails(loan);
        }
        loan.touch(caller.memberId());
        return homeLoanRepository.save(loan);
    }

    private void clearDetails(HomeLoan loan) {
        loan.setLender(null);
        loan.setLoanAmount(null);
        loan.setInterestRate(null);
        loan.setRepaymentFrequency(null);
        loan.setRepaymentAmount(null);
        loan.setHasOffset(false);
        loan.setOffsetBalance(null);
        loan.setOwnership(null);
        loan.setNotes(null);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
