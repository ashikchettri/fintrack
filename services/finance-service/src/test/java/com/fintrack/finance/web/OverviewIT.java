package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.BudgetLineRepository;
import com.fintrack.finance.repository.TransactionRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard rollup over real Postgres: the budget (plan) vs the latest month's
 * actual transactions (reality).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OverviewIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private BudgetLineRepository budgetLineRepository;

    @AfterEach
    void cleanup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        budgetLineRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId, UUID memberId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", memberId.toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private void txn(UUID household, UUID member, UUID accountId, LocalDate date, String amount) {
        txnCat(household, member, accountId, date, amount, null, null);
    }

    private void txnCat(UUID household, UUID member, UUID accountId, LocalDate date, String amount,
                        String bankCategory, String canonical) {
        transactionRepository.save(Transaction.builder()
                .householdId(household).memberId(member).accountId(accountId)
                .txnDate(date).description(bankCategory == null ? "t" : bankCategory)
                .category(bankCategory).canonicalCategory(canonical)
                .amount(new BigDecimal(amount)).currency("AUD")
                .visibility(Visibility.PERSONAL).source(TransactionSource.MANUAL)
                .dedupHash("h" + UUID.randomUUID()).build());
    }

    @Test
    void rollsUpPlanVsActual() throws Exception {
        UUID household = UUID.randomUUID();
        UUID me = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/household/budget").with(member(household, me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "AUD", "lines": [
                          {"section": "INCOME", "name": "Salary", "frequency": "MONTHLY", "amount": 10000},
                          {"section": "EXPENSE", "category": "Housing", "name": "Rent", "frequency": "MONTHLY", "amount": 2600},
                          {"section": "SAVING", "name": "Savings", "frequency": "MONTHLY", "amount": 2000}
                        ]}""")).andExpect(status().isOk());

        UUID accountId = accountRepository.save(
                new Account(household, me, "Everyday", AccountType.CHECKING, "AUD", BigDecimal.ZERO)).getId();
        // older month + the latest month (June) — actuals come from June only
        txn(household, me, accountId, LocalDate.of(2026, 5, 3), "-999");
        txn(household, me, accountId, LocalDate.of(2026, 6, 10), "-1500");
        txn(household, me, accountId, LocalDate.of(2026, 6, 20), "200");

        mockMvc.perform(get("/api/v1/household/overview").with(member(household, me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBudget").value(true))
                .andExpect(jsonPath("$.actualMonth").value("2026-06"))
                .andExpect(jsonPath("$.planned.income").value(10000.0))
                .andExpect(jsonPath("$.planned.expenses").value(2600.0))
                .andExpect(jsonPath("$.planned.leftover").value(5400.0))     // 10000 − 2600 − 2000
                .andExpect(jsonPath("$.actual.income").value(200.0))
                .andExpect(jsonPath("$.actual.expenses").value(1500.0));     // June only
    }

    @Test
    void breaksDownPlanVsActualByCanonicalCategory() throws Exception {
        UUID household = UUID.randomUUID();
        UUID me = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/household/budget").with(member(household, me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "AUD", "lines": [
                          {"section": "EXPENSE", "category": "Housing", "name": "Rent", "frequency": "MONTHLY", "amount": 2600},
                          {"section": "EXPENSE", "category": "Groceries & Food", "name": "Groceries", "frequency": "MONTHLY", "amount": 1000}
                        ]}""")).andExpect(status().isOk());

        UUID accountId = accountRepository.save(
                new Account(household, me, "Everyday", AccountType.CHECKING, "AUD", BigDecimal.ZERO)).getId();
        // stored canonical wins for the grocery row; the transport row has no
        // canonical, so the rollup derives it live from "Transportation"
        txnCat(household, me, accountId, LocalDate.of(2026, 6, 5), "-240", "Food & Drink", "GROCERIES");
        txnCat(household, me, accountId, LocalDate.of(2026, 6, 12), "-60", "Transportation", null);

        mockMvc.perform(get("/api/v1/household/overview").with(member(household, me)))
                .andExpect(status().isOk())
                // ordered by planned spend desc: Housing (2600) then Groceries (1000) then Transport (0)
                .andExpect(jsonPath("$.byCategory[0].category").value("Housing"))
                .andExpect(jsonPath("$.byCategory[0].planned").value(2600.0))
                .andExpect(jsonPath("$.byCategory[0].actual").value(0))
                .andExpect(jsonPath("$.byCategory[1].category").value("Groceries & Food"))
                .andExpect(jsonPath("$.byCategory[1].planned").value(1000.0))
                .andExpect(jsonPath("$.byCategory[1].actual").value(240.0))
                .andExpect(jsonPath("$.byCategory[2].category").value("Transport"))
                .andExpect(jsonPath("$.byCategory[2].planned").value(0))
                .andExpect(jsonPath("$.byCategory[2].actual").value(60.0));
    }

    @Test
    void emptyWhenNoBudgetAndNoTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/household/overview").with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBudget").value(false))
                .andExpect(jsonPath("$.actualMonth").isEmpty())
                .andExpect(jsonPath("$.planned.leftover").value(0))
                .andExpect(jsonPath("$.actual.expenses").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/overview")).andExpect(status().isUnauthorized());
    }
}
