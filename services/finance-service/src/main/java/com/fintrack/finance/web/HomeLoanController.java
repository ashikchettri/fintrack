package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.HomeLoanService;
import com.fintrack.finance.web.dto.HomeLoanRequest;
import com.fintrack.finance.web.dto.HomeLoanResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household's home-loan profile — the first slice of the financial profile
 * that feeds cash-flow + affordability calculations. Household-scoped: any
 * member can view or update it.
 */
@RestController
@RequestMapping("/api/v1/household/home-loan")
public class HomeLoanController {

    private final HomeLoanService homeLoanService;

    public HomeLoanController(HomeLoanService homeLoanService) {
        this.homeLoanService = homeLoanService;
    }

    @GetMapping
    public HomeLoanResponse get(@AuthenticationPrincipal Jwt jwt) {
        return homeLoanService.get(AuthenticatedMember.from(jwt))
                .map(HomeLoanResponse::from)
                .orElseGet(HomeLoanResponse::empty);
    }

    @PutMapping
    public HomeLoanResponse save(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody HomeLoanRequest request) {
        return HomeLoanResponse.from(homeLoanService.save(AuthenticatedMember.from(jwt), request));
    }
}
