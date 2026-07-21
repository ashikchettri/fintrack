package com.fintrack.insight.service;

import com.fintrack.insight.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls finance-service service-to-service (ADR 012), forwarding the caller's
 * bearer token — so finance-service applies its own household/member scoping and
 * insight-service can never read data the caller couldn't — plus the
 * X-Request-Id correlation id (ADR 010).
 */
@Component
public class FinanceClient {

    private final RestClient http;

    public FinanceClient(RestClient.Builder builder,
                         @Value("${insight.finance-uri:http://localhost:8082}") String financeUri) {
        this.http = builder.baseUrl(financeUri).build();
    }

    /** The caller's dashboard for {@code month} ("2026-07"), or the latest when null. */
    public FinanceDashboard dashboard(String bearerToken, String month) {
        String path = month == null ? "/api/v1/dashboard" : "/api/v1/dashboard?month=" + month;

        RestClient.RequestHeadersSpec<?> request = http.get()
                .uri(path)
                .header("Authorization", "Bearer " + bearerToken);

        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            request = request.header(CorrelationIdFilter.HEADER, traceId);
        }

        try {
            return request.retrieve().body(FinanceDashboard.class);
        } catch (RestClientException e) {
            throw new FinanceUnavailableException("Could not reach finance-service", e);
        }
    }
}
