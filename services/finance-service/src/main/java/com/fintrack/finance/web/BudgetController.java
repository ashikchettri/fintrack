package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.BudgetService;
import com.fintrack.finance.web.dto.BudgetRequest;
import com.fintrack.finance.web.dto.BudgetResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household budget (income / expenses / savings). Household-scoped; GET
 * returns the saved budget or the starter template, PUT replaces it wholesale.
 */
@RestController
@RequestMapping("/api/v1/household/budget")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public BudgetResponse get(@AuthenticationPrincipal Jwt jwt) {
        return budgetService.get(AuthenticatedMember.from(jwt));
    }

    @PutMapping
    public BudgetResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
        return budgetService.save(AuthenticatedMember.from(jwt), request);
    }
}
