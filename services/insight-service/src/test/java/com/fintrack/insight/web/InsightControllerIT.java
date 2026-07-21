package com.fintrack.insight.web;

import com.fintrack.insight.service.FinanceClient;
import com.fintrack.insight.service.FinanceDashboard;
import com.fintrack.insight.service.FinanceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The monthly-summary endpoint end to end (ADR 012): security, the correlation
 * id, and the template summary over a mocked finance-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InsightControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinanceClient financeClient;

    private static RequestPostProcessor member() {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", UUID.randomUUID().toString())
                        .claim("memberId", UUID.randomUUID().toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    @Test
    void returnsAMonthlySummaryAndEchoesTheCorrelationId() throws Exception {
        when(financeClient.dashboard(any(), any())).thenReturn(new FinanceDashboard("AUD", "2026-06", java.util.List.of("2026-06"),
                new FinanceDashboard.Totals(new BigDecimal("5000"), new BigDecimal("4120"), new BigDecimal("880"), 78),
                List.of(new FinanceDashboard.CategorySpend("Groceries & Food", new BigDecimal("1240"), 0.30)),
                List.of()));

        mockMvc.perform(get("/api/v1/insights/monthly-summary").param("month", "2026-06")
                        .with(member()).header("X-Request-Id", "trace-xyz-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-xyz-1"))
                .andExpect(jsonPath("$.month").value("2026-06"))
                .andExpect(jsonPath("$.totals.transactionCount").value(78))
                .andExpect(jsonPath("$.headline").value(org.hamcrest.Matchers.containsString("June 2026")))
                .andExpect(jsonPath("$.insights").isArray());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/insights/monthly-summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/insights/ask")).andExpect(status().isUnauthorized());
    }

    @Test
    void askReturns503WhenAiIsNotConfigured() throws Exception {
        // AI is off by default → no Anthropic client bean → Q&A can't run
        mockMvc.perform(post("/api/v1/insights/ask").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"how much on food in June?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI not configured"));
    }

    @Test
    void askRejectsABlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/insights/ask").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"));
    }

    @Test
    void surfacesAFinanceOutageAsABadGatewayProblem() throws Exception {
        when(financeClient.dashboard(any(), any()))
                .thenThrow(new FinanceUnavailableException("down", new RuntimeException()));

        mockMvc.perform(get("/api/v1/insights/monthly-summary").with(member())
                        .header("X-Request-Id", "trace-err-9"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Finance service unavailable"))
                .andExpect(jsonPath("$.traceId").value("trace-err-9"));
    }
}
