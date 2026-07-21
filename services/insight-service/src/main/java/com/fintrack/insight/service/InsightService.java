package com.fintrack.insight.service;

import com.fintrack.insight.service.SummaryGenerator.CategoryShare;
import com.fintrack.insight.service.SummaryGenerator.MonthlySummary;
import com.fintrack.insight.service.SummaryGenerator.SummaryInput;
import com.fintrack.insight.web.dto.MonthlySummaryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds the monthly spending summary (ADR 012): pull the caller's dashboard
 * from finance-service, reduce it to aggregate figures, and narrate them.
 */
@Service
public class InsightService {

    private static final int TOP_CATEGORIES = 5;

    private final FinanceClient financeClient;
    private final SummaryGenerator summaryGenerator;

    public InsightService(FinanceClient financeClient, SummaryGenerator summaryGenerator) {
        this.financeClient = financeClient;
        this.summaryGenerator = summaryGenerator;
    }

    public MonthlySummaryResponse monthlySummary(String bearerToken, String month) {
        FinanceDashboard dashboard = financeClient.dashboard(bearerToken, month);

        FinanceDashboard.Totals totals = dashboard.totals() == null
                ? new FinanceDashboard.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0)
                : dashboard.totals();

        List<CategoryShare> topCategories = dashboard.byCategory() == null ? List.of()
                : dashboard.byCategory().stream()
                        .limit(TOP_CATEGORIES)
                        .map(c -> new CategoryShare(c.category(), c.spent(), c.share()))
                        .toList();

        SummaryInput input = new SummaryInput(
                dashboard.month(), dashboard.currency(),
                totals.income(), totals.expenses(), totals.net(), totals.transactionCount(),
                topCategories);

        MonthlySummary summary = summaryGenerator.summarize(input);

        return new MonthlySummaryResponse(
                dashboard.month(),
                dashboard.currency(),
                new MonthlySummaryResponse.Totals(
                        totals.income(), totals.expenses(), totals.net(), totals.transactionCount()),
                summary.headline(),
                summary.insights());
    }
}
