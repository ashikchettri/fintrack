package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.DashboardService;
import com.fintrack.finance.web.dto.DashboardResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard read model in a single call — totals, category breakdown,
 * monthly trend, top merchants and recent activity — scoped to the caller.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.build(AuthenticatedMember.from(jwt));
    }
}
