package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.TransactionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared commitments over real Postgres (ADR 006). The headline test proves the
 * privacy boundary: a member's personal transactions never surface in the
 * household shared view, while shared ones do — across members.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SharedCommitmentsIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;

    private final UUID household = UUID.randomUUID();
    private final UUID memberA = UUID.randomUUID();
    private final UUID memberB = UUID.randomUUID();
    private UUID accountId;

    @BeforeEach
    void setUp() {
        // one account satisfies the FK; the scenario is about transactions
        accountId = accountRepository.save(
                new Account(household, memberA, "Everyday", AccountType.CHECKING, "AUD", BigDecimal.ZERO)).getId();
    }

    @AfterEach
    void cleanup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId, UUID memberId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", memberId.toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private UUID insert(UUID member, String desc, String category, String amount, Visibility visibility) {
        return transactionRepository.save(Transaction.builder()
                .householdId(household).memberId(member).accountId(accountId)
                .txnDate(LocalDate.of(2026, 7, 1)).description(desc).category(category)
                .amount(new BigDecimal(amount)).currency("AUD")
                .visibility(visibility).source(TransactionSource.MANUAL)
                .dedupHash("h" + UUID.randomUUID()).build()).getId();
    }

    private void seedHousehold() {
        insert(memberA, "Rent", "Housing", "-1000.00", Visibility.SHARED);
        insert(memberA, "Woolworths", "Groceries", "-200.00", Visibility.SHARED);
        insert(memberA, "Personal coffee", "Food & Drink", "-50.00", Visibility.PERSONAL); // must stay private
        insert(memberB, "Electricity", "Utilities", "-400.00", Visibility.SHARED);
        insert(memberB, "Personal book", "Personal", "-30.00", Visibility.PERSONAL);        // must stay private
    }

    @Test
    void householdViewShowsOnlySharedItemsAndSettlesEqually() throws Exception {
        seedHousehold();

        // member A: covered 1200 of a 1600 total, fair share 800 → owed 400
        mockMvc.perform(get("/api/v1/household/shared").with(member(household, memberA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShared").value(1600.0))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.fairShare").value(800.0))
                .andExpect(jsonPath("$.settlement.status").value("owed"))
                .andExpect(jsonPath("$.settlement.amount").value(400.0))
                .andExpect(jsonPath("$.settlement.yourContribution").value(1200.0))
                // only the 3 SHARED rows — the two personal ones are absent
                .andExpect(jsonPath("$.transactions.length()").value(3))
                .andExpect(jsonPath("$.transactions[?(@.description == 'Personal coffee')]").isEmpty())
                .andExpect(jsonPath("$.transactions[?(@.description == 'Personal book')]").isEmpty());

        // member B: covered 400, fair share 800 → owes 400
        mockMvc.perform(get("/api/v1/household/shared").with(member(household, memberB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.status").value("owes"))
                .andExpect(jsonPath("$.settlement.amount").value(400.0))
                .andExpect(jsonPath("$.settlement.yourContribution").value(400.0));
    }

    @Test
    void anotherHouseholdSeesNothing() throws Exception {
        seedHousehold();

        mockMvc.perform(get("/api/v1/household/shared").with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShared").value(0))
                .andExpect(jsonPath("$.memberCount").value(0))
                .andExpect(jsonPath("$.transactions.length()").value(0));
    }

    @Test
    void markingAPersonalTransactionSharedAddsItToTheHouseholdView() throws Exception {
        UUID coffee = insert(memberA, "Shared dinner", "Food & Drink", "-80.00", Visibility.PERSONAL);

        // not shared yet → not in the household view
        mockMvc.perform(get("/api/v1/household/shared").with(member(household, memberA)))
                .andExpect(jsonPath("$.transactions.length()").value(0));

        // A marks their own transaction as shared
        mockMvc.perform(patch("/api/v1/transactions/" + coffee + "/visibility")
                        .with(member(household, memberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"shared\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("shared"));

        // now it appears
        mockMvc.perform(get("/api/v1/household/shared").with(member(household, memberA)))
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.totalShared").value(80.0));
    }

    @Test
    void cannotMarkAnotherMembersTransaction() throws Exception {
        UUID aTxn = insert(memberA, "Rent", "Housing", "-1000.00", Visibility.PERSONAL);

        // member B tries to change A's transaction → 404 (member-scoped)
        mockMvc.perform(patch("/api/v1/transactions/" + aTxn + "/visibility")
                        .with(member(household, memberB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"shared\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transaction not found"));
    }

    @Test
    void invalidVisibilityValueIs400() throws Exception {
        UUID aTxn = insert(memberA, "Rent", "Housing", "-1000.00", Visibility.PERSONAL);

        mockMvc.perform(patch("/api/v1/transactions/" + aTxn + "/visibility")
                        .with(member(household, memberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"public\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.visibility").isNotEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/shared")).andExpect(status().isUnauthorized());
    }
}
