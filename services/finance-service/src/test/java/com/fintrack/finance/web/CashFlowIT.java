package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.BudgetLineRepository;
import com.fintrack.finance.repository.HomeLoanRepository;
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
 * Cash-flow snapshot over real Postgres: household income (monthly) minus the
 * caller's average monthly spending, with the loan repayment shown as context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CashFlowIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private BudgetLineRepository budgetLineRepository;
    @Autowired
    private HomeLoanRepository homeLoanRepository;

    @AfterEach
    void cleanup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        budgetLineRepository.deleteAll();
        homeLoanRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId, UUID memberId) {
        return jwt().jwt(b -> b
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", memberId.toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private void spend(UUID household, UUID member, UUID accountId, LocalDate date, String amount) {
        transactionRepository.save(Transaction.builder()
                .householdId(household).memberId(member).accountId(accountId)
                .txnDate(date).description("spend").amount(new BigDecimal(amount)).currency("AUD")
                .visibility(Visibility.PERSONAL).source(TransactionSource.MANUAL)
                .dedupHash("h" + UUID.randomUUID()).build());
    }

    @Test
    void computesMonthlySurplusFromIncomeAndSpending() throws Exception {
        UUID household = UUID.randomUUID();
        UUID me = UUID.randomUUID();

        // income from the budget's INCOME section: $90k/yr → $7,500/mo
        mockMvc.perform(put("/api/v1/household/budget").with(member(household, me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "AUD", "lines": [
                          {"section": "INCOME", "name": "Salary", "frequency": "ANNUALLY", "amount": 90000}
                        ]}""")).andExpect(status().isOk());
        // home loan: $3,000/mo repayment (shown as context)
        mockMvc.perform(put("/api/v1/household/home-loan").with(member(household, me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hasHomeLoan": true, "repaymentAmount": 3000, "repaymentFrequency": "MONTHLY"}"""))
                .andExpect(status().isOk());

        UUID accountId = accountRepository.save(
                new Account(household, me, "Everyday", AccountType.CHECKING, "AUD", BigDecimal.ZERO)).getId();
        // two months of spending: 3,000 and 4,000 → average 3,500/mo
        spend(household, me, accountId, LocalDate.of(2026, 5, 10), "-2000");
        spend(household, me, accountId, LocalDate.of(2026, 5, 20), "-1000");
        spend(household, me, accountId, LocalDate.of(2026, 6, 15), "-4000");

        mockMvc.perform(get("/api/v1/household/cash-flow").with(member(household, me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(7500.0))
                .andExpect(jsonPath("$.monthlyLoanRepayment").value(3000.0))
                .andExpect(jsonPath("$.monthlyAvgSpending").value(3500.0))
                .andExpect(jsonPath("$.monthlySurplus").value(4000.0))       // 7500 − 3500
                .andExpect(jsonPath("$.monthsOfSpendingData").value(2));
    }

    @Test
    void allZeroWhenNothingIsSetUp() throws Exception {
        mockMvc.perform(get("/api/v1/household/cash-flow").with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(0))
                .andExpect(jsonPath("$.monthlySurplus").value(0))
                .andExpect(jsonPath("$.monthsOfSpendingData").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/cash-flow")).andExpect(status().isUnauthorized());
    }
}
