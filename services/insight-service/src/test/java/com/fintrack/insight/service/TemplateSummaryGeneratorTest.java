package com.fintrack.insight.service;

import com.fintrack.insight.service.SummaryGenerator.CategoryShare;
import com.fintrack.insight.service.SummaryGenerator.MonthlySummary;
import com.fintrack.insight.service.SummaryGenerator.SummaryInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateSummaryGeneratorTest {

    private final TemplateSummaryGenerator generator = new TemplateSummaryGenerator();

    @Test
    void composesAHeadlineAndInsightsFromTheFigures() {
        MonthlySummary summary = generator.summarize(new SummaryInput(
                "2026-06", "AUD",
                new BigDecimal("5000.00"), new BigDecimal("4120.00"), new BigDecimal("880.00"), 78,
                List.of(new CategoryShare("Groceries & Food", new BigDecimal("1240.00"), 0.30),
                        new CategoryShare("Transport", new BigDecimal("620.00"), 0.15))));

        assertThat(summary.headline())
                .contains("June 2026").contains("4,120").contains("78 transactions");
        assertThat(summary.insights())
                .anyMatch(s -> s.contains("ahead by") && s.contains("880"))
                .anyMatch(s -> s.contains("Groceries & Food") && s.contains("30%"))
                .anyMatch(s -> s.contains("Transport"));
    }

    @Test
    void callsOutSpendingMoreThanEarned() {
        MonthlySummary summary = generator.summarize(new SummaryInput(
                "2026-06", "AUD",
                new BigDecimal("3000.00"), new BigDecimal("3500.00"), new BigDecimal("-500.00"), 40, List.of()));

        assertThat(summary.insights()).anyMatch(s -> s.contains("more than you earned") && s.contains("500"));
    }

    @Test
    void handlesAnEmptyMonth() {
        MonthlySummary summary = generator.summarize(new SummaryInput(
                "2026-06", "AUD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of()));

        assertThat(summary.headline()).contains("No transactions");
        assertThat(summary.insights()).isEmpty();
    }

    @Test
    void formatsAKnownMonthAndFallsBackForNullOrBadInput() {
        assertThat(TemplateSummaryGenerator.period("2026-06")).isEqualTo("June 2026");
        assertThat(TemplateSummaryGenerator.period(null)).isEqualTo("this period");
        assertThat(TemplateSummaryGenerator.period("garbage")).isEqualTo("garbage");
    }
}
