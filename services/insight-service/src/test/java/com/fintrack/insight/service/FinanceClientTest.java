package com.fintrack.insight.service;

import com.fintrack.insight.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinanceClientTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private FinanceClient clientBackedBy(MockServerHolder holder) {
        return holder.client;
    }

    /** Wires a FinanceClient to a MockRestServiceServer via a shared builder. */
    private static final class MockServerHolder {
        final MockRestServiceServer server;
        final FinanceClient client;

        MockServerHolder() {
            RestClient.Builder builder = RestClient.builder();
            this.server = MockRestServiceServer.bindTo(builder).build();
            this.client = new FinanceClient(builder, "http://finance-service:8082");
        }
    }

    @Test
    void forwardsTheBearerTokenAndCorrelationIdAndParsesTheDashboard() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-abc-123");
        MockServerHolder holder = new MockServerHolder();

        holder.server.expect(requestTo("http://finance-service:8082/api/v1/dashboard?month=2026-06"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-value"))
                .andExpect(header(CorrelationIdFilter.HEADER, "trace-abc-123"))
                .andRespond(withSuccess("""
                        {"currency": "AUD", "month": "2026-06",
                         "totals": {"income": 5000, "expenses": 4120, "net": 880, "transactionCount": 78},
                         "byCategory": [{"category": "Groceries & Food", "spent": 1240, "share": 0.3}],
                         "topMerchants": []}""", MediaType.APPLICATION_JSON));

        FinanceDashboard dashboard = clientBackedBy(holder).dashboard("jwt-value", "2026-06");

        assertThat(dashboard.currency()).isEqualTo("AUD");
        assertThat(dashboard.totals().transactionCount()).isEqualTo(78);
        assertThat(dashboard.byCategory()).hasSize(1);
        holder.server.verify();
    }

    @Test
    void omitsTheMonthParamWhenNull() {
        MockServerHolder holder = new MockServerHolder();
        holder.server.expect(requestTo("http://finance-service:8082/api/v1/dashboard"))
                .andRespond(withSuccess("{\"currency\":\"AUD\"}", MediaType.APPLICATION_JSON));

        assertThat(clientBackedBy(holder).dashboard("jwt-value", null).currency()).isEqualTo("AUD");
        holder.server.verify();
    }

    @Test
    void wrapsADownstreamErrorAsFinanceUnavailable() {
        MockServerHolder holder = new MockServerHolder();
        holder.server.expect(requestTo("http://finance-service:8082/api/v1/dashboard"))
                .andRespond(withServerError());

        assertThatExceptionOfType(FinanceUnavailableException.class)
                .isThrownBy(() -> clientBackedBy(holder).dashboard("jwt-value", null));
    }
}
