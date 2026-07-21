package com.fintrack.insight.service;

import com.fintrack.insight.service.ai.AnthropicChatClient;
import com.fintrack.insight.service.ai.ClaudeResponse;
import com.fintrack.insight.service.ai.ClaudeResponse.ContentBlock;
import com.fintrack.insight.web.dto.AnswerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceTest {

    private final AnthropicChatClient client = mock(AnthropicChatClient.class);
    private final FinanceClient financeClient = mock(FinanceClient.class);

    @SuppressWarnings("unchecked")
    private QuestionService serviceWith(AnthropicChatClient available) {
        ObjectProvider<AnthropicChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        return new QuestionService(provider, financeClient, JsonMapper.builder().build());
    }

    private static ClaudeResponse toolUse(String month) {
        return new ClaudeResponse("tool_use", List.of(
                new ContentBlock("tool_use", null, "tu_1", "get_spending", Map.of("month", month))));
    }

    private static ClaudeResponse answer(String text) {
        return new ClaudeResponse("end_turn", List.of(new ContentBlock("text", text, null, null, null)));
    }

    private static FinanceDashboard dashboard() {
        return new FinanceDashboard("AUD", "2026-06", List.of("2026-06"),
                new FinanceDashboard.Totals(new BigDecimal("5000"), new BigDecimal("4120"), new BigDecimal("880"), 78),
                List.of(new FinanceDashboard.CategorySpend("Groceries & Food", new BigDecimal("1240"), 0.30)),
                List.of());
    }

    @Test
    void runsTheToolLoopAndReturnsAGroundedAnswer() {
        QuestionService service = serviceWith(client);
        when(client.converse(any(), any(), any()))
                .thenReturn(toolUse("2026-06"), answer("You spent AUD 1,240 on groceries in June."));
        when(financeClient.dashboard(eq("bearer"), eq("2026-06"))).thenReturn(dashboard());

        AnswerResponse response = service.ask("bearer", "How much on food in June?");

        assertThat(response.answer()).isEqualTo("You spent AUD 1,240 on groceries in June.");
        // the model's requested month was executed against finance-service
        verify(financeClient).dashboard("bearer", "2026-06");
    }

    @Test
    void answersDirectlyWhenNoToolIsNeeded() {
        QuestionService service = serviceWith(client);
        when(client.converse(any(), any(), any())).thenReturn(answer("Ask me about a specific month."));

        assertThat(service.ask("bearer", "hi").answer()).isEqualTo("Ask me about a specific month.");
    }

    @Test
    void fallsBackWhenTheModelReturnsNoText() {
        QuestionService service = serviceWith(client);
        when(client.converse(any(), any(), any())).thenReturn(new ClaudeResponse("end_turn", List.of()));

        assertThat(service.ask("bearer", "?").answer()).contains("couldn't find an answer");
    }

    @Test
    void stopsAtTheTurnCapWhenTheModelKeepsCallingTools() {
        QuestionService service = serviceWith(client);
        when(client.converse(any(), any(), any())).thenReturn(toolUse("2026-06"));   // never ends
        when(financeClient.dashboard(any(), any())).thenReturn(dashboard());

        AnswerResponse response = service.ask("bearer", "loop please");

        assertThat(response.answer()).contains("more steps than I can work through");
        verify(client, times(5)).converse(any(), any(), any());   // MAX_TURNS
    }

    @Test
    void rejectsWhenAiIsNotConfigured() {
        QuestionService service = serviceWith(null);   // no Anthropic client bean

        assertThatExceptionOfType(AiNotConfiguredException.class)
                .isThrownBy(() -> service.ask("bearer", "anything"));
    }
}
