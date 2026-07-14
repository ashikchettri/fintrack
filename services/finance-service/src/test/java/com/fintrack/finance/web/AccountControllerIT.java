package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.repository.AccountRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack over real Postgres. The JWKS signature check is covered by
 * JwksSecurityIntegrationTest; here we use the jwt() post-processor to set the
 * household/member/role claims and focus on the accounts contract + scoping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    /** A caller identity: a JWT carrying the household/member/role claims. */
    private static RequestPostProcessor member(UUID householdId, UUID memberId) {
        return jwt().jwt(builder -> builder
                .subject(UUID.randomUUID().toString())
                .claim("householdId", householdId.toString())
                .claim("memberId", memberId.toString())
                .claim("role", "OWNER"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private static String body(String name, String type, String currency, String opening) {
        return """
                {"name": "%s", "type": "%s", "currency": "%s", "openingBalance": %s}"""
                .formatted(name, type, currency, opening);
    }

    @Test
    void createThenListReturnsTheAccount() throws Exception {
        UUID household = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts").with(member(household, memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Everyday", "CHECKING", "aud", "250.75")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Everyday"))
                .andExpect(jsonPath("$.type").value("CHECKING"))
                .andExpect(jsonPath("$.currency").value("AUD"))          // normalized
                .andExpect(jsonPath("$.openingBalance").value(250.75));

        mockMvc.perform(get("/api/v1/accounts").with(member(household, memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Everyday"));
    }

    @Test
    void oneMemberCannotSeeAnothersAccounts() throws Exception {
        UUID householdA = UUID.randomUUID();
        UUID householdB = UUID.randomUUID();

        // A creates an account
        String id = JsonPath.read(
                mockMvc.perform(post("/api/v1/accounts").with(member(householdA, UUID.randomUUID()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("A's account", "SAVINGS", "AUD", "0")))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        // B sees an empty list and cannot fetch A's account by id — the scoping test
        mockMvc.perform(get("/api/v1/accounts").with(member(householdB, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/accounts/" + id).with(member(householdB, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void deleteRemovesOnlyYourOwnAccount() throws Exception {
        UUID household = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String id = JsonPath.read(
                mockMvc.perform(post("/api/v1/accounts").with(member(household, memberId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Temp", "CASH", "AUD", "0")))
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        // another household can't delete it (404)
        mockMvc.perform(delete("/api/v1/accounts/" + id)
                        .with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound());

        // the owner can
        mockMvc.perform(delete("/api/v1/accounts/" + id).with(member(household, memberId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/accounts/" + id).with(member(household, memberId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(member(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "CHECKING", "dollars", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.currency").isNotEmpty());
    }

    @Test
    void unknownAccountTypeIs400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(member(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("X", "CRYPTO_WALLET", "AUD", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestsWithoutATokenAre401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }
}
