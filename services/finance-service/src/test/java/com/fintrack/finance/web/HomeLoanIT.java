package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.repository.HomeLoanRepository;
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
 * Home-loan profile over real Postgres: upsert, read-back, "no loan" clears the
 * details, household scoping, and validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomeLoanIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HomeLoanRepository homeLoanRepository;

    @AfterEach
    void cleanup() {
        homeLoanRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", UUID.randomUUID().toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private static final String FULL = """
            {"hasHomeLoan": true, "lender": "ANZ", "loanAmount": 650000, "interestRate": 6.25,
             "repaymentFrequency": "MONTHLY", "repaymentAmount": 3800, "hasOffset": true,
             "offsetBalance": 45000, "ownership": "JOINT", "currency": "aud", "notes": "family home"}""";

    @Test
    void savesAndReadsBackTheProfile() throws Exception {
        UUID household = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHomeLoan").value(true))
                .andExpect(jsonPath("$.loanAmount").value(650000.0))
                .andExpect(jsonPath("$.interestRate").value(6.25))
                .andExpect(jsonPath("$.repaymentFrequency").value("MONTHLY"))
                .andExpect(jsonPath("$.ownership").value("JOINT"))
                .andExpect(jsonPath("$.hasOffset").value(true))
                .andExpect(jsonPath("$.offsetBalance").value(45000.0))
                .andExpect(jsonPath("$.currency").value("AUD"));   // normalized

        mockMvc.perform(get("/api/v1/household/home-loan").with(member(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lender").value("ANZ"))
                .andExpect(jsonPath("$.loanAmount").value(650000.0));
    }

    @Test
    void updatingReplacesInPlaceAndOneProfilePerHousehold() throws Exception {
        UUID household = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                .contentType(MediaType.APPLICATION_JSON).content(FULL)).andExpect(status().isOk());

        // update the rate
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL.replace("6.25", "5.99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestRate").value(5.99));

        // still exactly one row for the household
        org.assertj.core.api.Assertions.assertThat(homeLoanRepository.findAll()).hasSize(1);
    }

    @Test
    void sayingNoLoanClearsTheDetails() throws Exception {
        UUID household = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                .contentType(MediaType.APPLICATION_JSON).content(FULL)).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hasHomeLoan": false, "hasOffset": false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHomeLoan").value(false))
                .andExpect(jsonPath("$.loanAmount").isEmpty())
                .andExpect(jsonPath("$.repaymentFrequency").isEmpty());
    }

    @Test
    void emptyForAHouseholdThatHasntSetOne() throws Exception {
        mockMvc.perform(get("/api/v1/household/home-loan").with(member(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHomeLoan").value(false))
                .andExpect(jsonPath("$.loanAmount").isEmpty());
    }

    @Test
    void anotherHouseholdDoesNotSeeThisOne() throws Exception {
        UUID household = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household))
                .contentType(MediaType.APPLICATION_JSON).content(FULL)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/household/home-loan").with(member(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHomeLoan").value(false));
    }

    @Test
    void anOutOfRangeInterestRateIs400() throws Exception {
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hasHomeLoan": true, "interestRate": 150}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.interestRate").isNotEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/home-loan")).andExpect(status().isUnauthorized());
    }
}
