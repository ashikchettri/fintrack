package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.repository.NetWorthItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Net worth over real Postgres (ADR 014): the replace-all balance sheet and the
 * summary that folds the home loan in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NetWorthIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NetWorthItemRepository itemRepository;

    @AfterEach
    void cleanup() {
        itemRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", UUID.randomUUID().toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    @Test
    void savesTheBalanceSheetAndComputesNetWorth() throws Exception {
        UUID household = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/household/net-worth/items").with(member(household))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "AUD", "items": [
                          {"kind": "ASSET", "category": "Property", "name": "Home", "value": 800000},
                          {"kind": "ASSET", "category": "Savings & cash", "name": "Savings", "value": 50000},
                          {"kind": "LIABILITY", "category": "Credit card", "name": "Visa", "value": 3000}
                        ]}""")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/household/net-worth/items").with(member(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        mockMvc.perform(get("/api/v1/household/net-worth").with(member(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssets").value(850000.0))
                .andExpect(jsonPath("$.totalLiabilities").value(3000.0))
                .andExpect(jsonPath("$.netWorth").value(847000.0));
    }

    @Test
    void foldsTheHomeLoanIntoTheSummary() throws Exception {
        UUID household = UUID.randomUUID();

        // a home loan with an offset — no manual items
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hasHomeLoan": true, "loanAmount": 500000, "hasOffset": true,
                         "offsetBalance": 120000, "currency": "AUD"}""")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/household/net-worth").with(member(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssets").value(120000.0))       // offset
                .andExpect(jsonPath("$.totalLiabilities").value(500000.0))  // loan
                .andExpect(jsonPath("$.netWorth").value(-380000.0))
                .andExpect(jsonPath("$.liabilities[0].source").value("HOME_LOAN"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/net-worth")).andExpect(status().isUnauthorized());
    }
}
