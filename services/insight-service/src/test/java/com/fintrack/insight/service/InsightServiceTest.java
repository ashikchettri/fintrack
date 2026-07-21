package com.fintrack.insight.service;

import com.fintrack.insight.web.dto.MonthlySummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightServiceTest {

    private final FinanceClient financeClient = mock(FinanceClient.class);
    // the real template generator — exercises the whole reduction end to end
    private final InsightService service = new InsightService(financeClient, new TemplateSummaryGenerator());

    @Test
    void reducesTheDashboardToASummary() {
        when(financeClient.dashboard(eq("bearer"), eq("2026-06"))).thenReturn(new FinanceDashboard("AUD", "2026-06", java.util.List.of("2026-06"),
                new FinanceDashboard.Totals(new BigDecimal("5000"), new BigDecimal("4120"), new BigDecimal("880"), 78),
                List.of(new FinanceDashboard.CategorySpend("Groceries & Food", new BigDecimal("1240"), 0.30)),
                List.of()));

        MonthlySummaryResponse response = service.monthlySummary("bearer", "2026-06");

        assertThat(response.month()).isEqualTo("2026-06");
        assertThat(response.currency()).isEqualTo("AUD");
        assertThat(response.totals().transactionCount()).isEqualTo(78);
        assertThat(response.headline()).contains("June 2026");
        assertThat(response.insights()).isNotEmpty();
    }

    @Test
    void toleratesAnEmptyDashboard() {
        when(financeClient.dashboard(any(), any()))
                .thenReturn(new FinanceDashboard(null, null, null, null, null, null));

        MonthlySummaryResponse response = service.monthlySummary("bearer", null);

        assertThat(response.totals().transactionCount()).isZero();
        assertThat(response.headline()).contains("No transactions");
    }
}
