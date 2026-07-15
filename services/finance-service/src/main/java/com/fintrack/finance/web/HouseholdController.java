package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.SharedCommitmentService;
import com.fintrack.finance.web.dto.SharedHouseholdView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The private household view of shared commitments (ADR 006) — only shared items
 * and agreed totals, scoped to the caller's household. A member's personal
 * transactions are never reachable here.
 */
@RestController
@RequestMapping("/api/v1/household")
public class HouseholdController {

    private final SharedCommitmentService sharedCommitmentService;

    public HouseholdController(SharedCommitmentService sharedCommitmentService) {
        this.sharedCommitmentService = sharedCommitmentService;
    }

    @GetMapping("/shared")
    public SharedHouseholdView shared(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) String month) {
        return sharedCommitmentService.sharedView(AuthenticatedMember.from(jwt), MonthParam.parse(month));
    }
}
