package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.repository.IncomeRepository;
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
 * Income over real Postgres: a member's own upsert + annualization, the
 * household summary across members, scoping, and validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IncomeIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IncomeRepository incomeRepository;

    @AfterEach
    void cleanup() {
        incomeRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId, UUID memberId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", memberId.toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private void putIncome(UUID household, UUID memberId, String json) throws Exception {
        mockMvc.perform(put("/api/v1/household/income").with(member(household, memberId))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @Test
    void savesOwnIncomeAndAnnualizesIt() throws Exception {
        UUID household = UUID.randomUUID();
        UUID me = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/household/income").with(member(household, me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"salaryAmount": 2000, "salaryFrequency": "FORTNIGHTLY",
                                 "superRate": 11.5, "bonusAnnual": 12000, "currency": "aud"}"""))
                .andExpect(status().isOk())
                // 2000 x 26 + 12000 bonus = 64000
                .andExpect(jsonPath("$.annualIncome").value(64000.0))
                .andExpect(jsonPath("$.salaryFrequency").value("FORTNIGHTLY"))
                .andExpect(jsonPath("$.currency").value("AUD"));

        mockMvc.perform(get("/api/v1/household/income").with(member(household, me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryAmount").value(2000.0))
                .andExpect(jsonPath("$.superRate").value(11.5));
    }

    @Test
    void summaryTotalsBothPartners() throws Exception {
        UUID household = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        UUID partner = UUID.randomUUID();

        putIncome(household, me, """
                {"salaryAmount": 2000, "salaryFrequency": "FORTNIGHTLY", "bonusAnnual": 12000}""");
        putIncome(household, partner, """
                {"salaryAmount": 90000, "salaryFrequency": "ANNUALLY"}""");

        mockMvc.perform(get("/api/v1/household/income/summary").with(member(household, me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualTotal").value(154000.0))     // 64000 + 90000
                .andExpect(jsonPath("$.members.length()").value(2))
                // higher earner first; the caller's own entry is flagged
                .andExpect(jsonPath("$.members[0].annualIncome").value(90000.0))
                .andExpect(jsonPath("$.members[?(@.isYou == true)].annualIncome").value(64000.0));
    }

    @Test
    void updatingReplacesOwnRowInPlace() throws Exception {
        UUID household = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        putIncome(household, me, """
                {"salaryAmount": 80000, "salaryFrequency": "ANNUALLY"}""");
        putIncome(household, me, """
                {"salaryAmount": 95000, "salaryFrequency": "ANNUALLY"}""");

        org.assertj.core.api.Assertions.assertThat(incomeRepository.findAll()).hasSize(1);
        mockMvc.perform(get("/api/v1/household/income").with(member(household, me)))
                .andExpect(jsonPath("$.annualIncome").value(95000.0));
    }

    @Test
    void emptyForAMemberWhoHasntSetIncome() throws Exception {
        mockMvc.perform(get("/api/v1/household/income").with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualIncome").value(0))
                .andExpect(jsonPath("$.salaryAmount").isEmpty());
    }

    @Test
    void anotherHouseholdHasItsOwnSummary() throws Exception {
        UUID household = UUID.randomUUID();
        putIncome(household, UUID.randomUUID(), """
                {"salaryAmount": 90000, "salaryFrequency": "ANNUALLY"}""");

        mockMvc.perform(get("/api/v1/household/income/summary").with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualTotal").value(0))
                .andExpect(jsonPath("$.members.length()").value(0));
    }

    @Test
    void anOutOfRangeSuperRateIs400() throws Exception {
        mockMvc.perform(put("/api/v1/household/income").with(member(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"superRate": 200}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.superRate").isNotEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/income")).andExpect(status().isUnauthorized());
    }
}
