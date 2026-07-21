package com.fintrack.insight.web;

import com.fintrack.insight.service.InsightService;
import com.fintrack.insight.service.QuestionService;
import com.fintrack.insight.web.dto.AnswerResponse;
import com.fintrack.insight.web.dto.AskRequest;
import com.fintrack.insight.web.dto.MonthlySummaryResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final QuestionService questionService;

    public InsightController(InsightService insightService, QuestionService questionService) {
        this.insightService = insightService;
        this.questionService = questionService;
    }

    @GetMapping("/monthly-summary")
    public MonthlySummaryResponse monthlySummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "month", required = false) String month) {
        // jwt.getTokenValue() is the raw bearer token, forwarded to finance-service
        return insightService.monthlySummary(jwt.getTokenValue(), month);
    }

    /** Ask a natural-language question about your spending (ADR 013). */
    @PostMapping("/ask")
    public AnswerResponse ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return questionService.ask(jwt.getTokenValue(), request.question());
    }
}
