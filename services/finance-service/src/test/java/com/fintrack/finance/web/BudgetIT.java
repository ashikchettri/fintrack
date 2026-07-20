package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.repository.BudgetLineRepository;
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
 * Household budget over real Postgres: the starter template, replace-all save +
 * read-back, dropping blank rows, household scoping, and validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BudgetLineRepository budgetLineRepository;

    @AfterEach
    void cleanup() {
        budgetLineRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", UUID.randomUUID().toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private static final String THREE_LINES = """
            {"currency": "aud", "lines": [
              {"section": "INCOME", "name": "Salary", "frequency": "MONTHLY", "amount": 10000},
              {"section": "EXPENSE", "category": "Housing", "name": "Rent", "frequency": "WEEKLY", "amount": 600},
              {"section": "SAVING", "name": "Emergency fund", "frequency": "MONTHLY", "amount": 2000}
            ]}""";

    @Test
    void returnsTheStarterTemplateBeforeAnythingIsSaved() throws Exception {
        mockMvc.perform(get("/api/v1/household/budget").with(member(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.lines.length()").value(org.hamcrest.Matchers.greaterThan(40)))
                .andExpect(jsonPath("$.lines[?(@.name=='Mortgage repayments')].category").value("Housing"));
    }

    @Test
    void savesAndReadsBackTheBudget() throws Exception {
        UUID household = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/household/budget").with(member(household))
                        .contentType(MediaType.APPLICATION_JSON).content(THREE_LINES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("AUD"))   // normalized
                .andExpect(jsonPath("$.lines.length()").value(3));

        mockMvc.perform(get("/api/v1/household/budget").with(member(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[1].name").value("Rent"))
                .andExpect(jsonPath("$.lines[1].amount").value(600.0));
    }

    @Test
    void saveReplacesTheWholeBudgetAndDropsBlankRows() throws Exception {
        UUID household = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/household/budget").with(member(household))
                .contentType(MediaType.APPLICATION_JSON).content(THREE_LINES)).andExpect(status().isOk());

        // replace with one real line + one blank row (no name) → only the real one persists
        mockMvc.perform(put("/api/v1/household/budget").with(member(household))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency": "AUD", "lines": [
                                  {"section": "INCOME", "name": "Wage", "frequency": "FORTNIGHTLY", "amount": 3000},
                                  {"section": "EXPENSE", "category": "Housing", "name": "", "frequency": "MONTHLY"}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].name").value("Wage"));
    }

    @Test
    void anotherHouseholdSeesTheTemplateNotThisBudget() throws Exception {
        UUID household = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/household/budget").with(member(household))
                .contentType(MediaType.APPLICATION_JSON).content(THREE_LINES)).andExpect(status().isOk());

        // a different household still gets the full starter template
        mockMvc.perform(get("/api/v1/household/budget").with(member(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(org.hamcrest.Matchers.greaterThan(40)));
    }

    @Test
    void aNegativeAmountIs400() throws Exception {
        mockMvc.perform(put("/api/v1/household/budget").with(member(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines": [{"section": "EXPENSE", "name": "Rent", "amount": -5}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/budget")).andExpect(status().isUnauthorized());
    }
}
