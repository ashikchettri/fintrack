package com.fintrack.finance.service;

import com.fintrack.finance.domain.BudgetLine;
import com.fintrack.finance.repository.BudgetLineRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.BudgetLineDto;
import com.fintrack.finance.web.dto.BudgetRequest;
import com.fintrack.finance.web.dto.BudgetResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The household budget (jointly held → household-scoped). Saved wholesale
 * (replace-all): the UI edits the whole sheet and PUTs it back. A household that
 * hasn't saved one yet gets the {@link BudgetTemplate} to fill in.
 */
@Service
public class BudgetService {

    private final BudgetLineRepository budgetLineRepository;

    public BudgetService(BudgetLineRepository budgetLineRepository) {
        this.budgetLineRepository = budgetLineRepository;
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(AuthenticatedMember caller) {
        List<BudgetLine> saved = budgetLineRepository.findByHouseholdIdOrderBySortOrder(caller.householdId());
        if (saved.isEmpty()) {
            return new BudgetResponse("AUD", BudgetTemplate.defaultLines());
        }
        return new BudgetResponse(saved.get(0).getCurrency(), saved.stream().map(BudgetLineDto::from).toList());
    }

    @Transactional
    public BudgetResponse save(AuthenticatedMember caller, BudgetRequest req) {
        budgetLineRepository.deleteByHouseholdId(caller.householdId());

        String currency = req.currency() == null ? "AUD" : req.currency().toUpperCase(Locale.ROOT);
        List<BudgetLineDto> incoming = req.lines() == null ? List.of() : req.lines();

        List<BudgetLine> toSave = new ArrayList<>();
        int order = 0;
        for (BudgetLineDto dto : incoming) {
            // drop blank rows (a template line the user never filled a name for)
            if (dto.section() == null || dto.name() == null || dto.name().isBlank()) {
                continue;
            }
            toSave.add(new BudgetLine(caller.householdId(), dto.section(),
                    trim(dto.category()), dto.name().strip(), dto.frequency(), dto.amount(), order++, currency));
        }
        budgetLineRepository.saveAll(toSave);
        return new BudgetResponse(currency, toSave.stream().map(BudgetLineDto::from).toList());
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
