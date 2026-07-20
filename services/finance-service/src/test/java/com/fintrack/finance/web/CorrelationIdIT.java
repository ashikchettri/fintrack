package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The request correlation id (ADR 010): finance-service honors an inbound
 * X-Request-Id (minted by the gateway), mints one when absent, echoes it on the
 * response, and stamps it into every ProblemDetail so an error ties to its logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorrelationIdIT {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor member() {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", UUID.randomUUID().toString())
                        .claim("memberId", UUID.randomUUID().toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    @Test
    void echoesAnInboundRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").with(member()).header("X-Request-Id", "gateway-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "gateway-abc-123"));
    }

    @Test
    void mintsARequestIdWhenNoneIsSent() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").with(member()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[A-Za-z0-9_-]{8,64}")));
    }

    @Test
    void stampsTheTraceIdIntoErrorBodies() throws Exception {
        // no file part → MissingServletRequestPartException → 400 ProblemDetail
        mockMvc.perform(multipart("/api/v1/imports/transactions").param("currency", "AUD")
                        .with(member()).header("X-Request-Id", "err-trace-9999"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "err-trace-9999"))
                .andExpect(jsonPath("$.traceId").value("err-trace-9999"));
    }
}
