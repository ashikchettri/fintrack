package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.IncomeService;
import com.fintrack.finance.web.dto.HouseholdIncomeResponse;
import com.fintrack.finance.web.dto.IncomeRequest;
import com.fintrack.finance.web.dto.IncomeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Income: a member edits their own (member-scoped); the /summary sums across
 * the household. Part of the financial profile that feeds cash-flow +
 * affordability.
 */
@RestController
@RequestMapping("/api/v1/household/income")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @GetMapping
    public IncomeResponse get(@AuthenticationPrincipal Jwt jwt) {
        return incomeService.get(AuthenticatedMember.from(jwt))
                .map(IncomeResponse::from)
                .orElseGet(IncomeResponse::empty);
    }

    @PutMapping
    public IncomeResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IncomeRequest request) {
        return IncomeResponse.from(incomeService.save(AuthenticatedMember.from(jwt), request));
    }

    @GetMapping("/summary")
    public HouseholdIncomeResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return incomeService.householdSummary(AuthenticatedMember.from(jwt));
    }
}
