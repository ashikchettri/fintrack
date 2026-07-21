package com.fintrack.insight.web;

import com.fintrack.insight.service.InsightService;
import com.fintrack.insight.web.dto.MonthlySummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI spending insights (ADR 012). Forwards the caller's JWT to finance-service,
 * so the summary only ever covers data the caller is allowed to see.
 */
@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/monthly-summary")
    public MonthlySummaryResponse monthlySummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "month", required = false) String month) {
        // jwt.getTokenValue() is the raw bearer token, forwarded to finance-service
        return insightService.monthlySummary(jwt.getTokenValue(), month);
    }
}
