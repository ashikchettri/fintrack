package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.OverviewService;
import com.fintrack.finance.web.dto.OverviewResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard rollup: the household budget (plan) vs the latest month's
 * actual transactions (reality).
 */
@RestController
@RequestMapping("/api/v1/household/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public OverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return overviewService.overview(AuthenticatedMember.from(jwt));
    }
}
