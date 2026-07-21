package com.fintrack.insight.service.ai;

import com.fintrack.insight.service.SummaryGenerator.MonthlySummary;
import com.fintrack.insight.service.SummaryGenerator.SummaryInput;
import com.fintrack.insight.service.TemplateSummaryGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeSummaryGeneratorTest {

    private final AnthropicChatClient client = mock(AnthropicChatClient.class);
    private final ClaudeSummaryGenerator generator =
            new ClaudeSummaryGenerator(client, new TemplateSummaryGenerator(), JsonMapper.builder().build());

    private static final SummaryInput INPUT = new SummaryInput(
            "2026-06", "AUD",
            new BigDecimal("5000"), new BigDecimal("4120"), new BigDecimal("880"), 78,
            List.of(new com.fintrack.insight.service.SummaryGenerator.CategoryShare(
                    "Groceries & Food", new BigDecimal("1240"), 0.30)));

    @Test
    void usesTheModelsHeadlineAndInsights() {
        when(client.complete(anyString(), anyString())).thenReturn("""
                {"headline": "A steady month.", "insights": ["Groceries led your spend.", "You saved $880."]}""");

        MonthlySummary summary = generator.summarize(INPUT);

        assertThat(summary.headline()).isEqualTo("A steady month.");
        assertThat(summary.insights()).containsExactly("Groceries led your spend.", "You saved $880.");
    }

    @Test
    void toleratesProseAroundTheJsonObject() {
        when(client.complete(anyString(), anyString()))
                .thenReturn("Here you go:\n{\"headline\": \"Nice month.\", \"insights\": []}\nThanks!");

        assertThat(generator.summarize(INPUT).headline()).isEqualTo("Nice month.");
    }

    @Test
    void fallsBackToTheTemplateOnAMalformedResponse() {
        when(client.complete(anyString(), anyString())).thenReturn("not json at all");

        // template headline mentions the month + spend
        assertThat(generator.summarize(INPUT).headline()).contains("June 2026");
    }

    @Test
    void fallsBackToTheTemplateWhenTheModelCallThrows() {
        when(client.complete(anyString(), anyString())).thenThrow(new RuntimeException("503"));

        assertThat(generator.summarize(INPUT).headline()).contains("June 2026");
    }

    @Test
    void skipsTheModelEntirelyForAnEmptyMonth() {
        SummaryInput empty = new SummaryInput("2026-06", "AUD",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());

        assertThat(generator.summarize(empty).headline()).contains("No transactions");
    }
}
