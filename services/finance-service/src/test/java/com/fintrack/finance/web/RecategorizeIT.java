package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recategorize over real Postgres (ADR 009). AI is off in tests, so the rule
 * mapper does the work — enough to prove the endpoint reviews the caller's rows,
 * updates only the ones whose canonical category changed, and never touches
 * another member's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecategorizeIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;

    private final UUID household = UUID.randomUUID();
    private final UUID memberA = UUID.randomUUID();
    private final UUID memberB = UUID.randomUUID();

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

    private UUID account(UUID member) {
        return accountRepository.save(
                new Account(household, member, "Everyday", AccountType.CHECKING, "AUD", BigDecimal.ZERO)).getId();
    }

    private void txn(UUID member, UUID accountId, String description, String bankCategory, String canonical) {
        transactionRepository.save(Transaction.builder()
                .householdId(household).memberId(member).accountId(accountId)
                .txnDate(LocalDate.of(2026, 6, 1)).description(description)
                .category(bankCategory).canonicalCategory(canonical)
                .amount(new BigDecimal("-50")).currency("AUD")
                .visibility(Visibility.PERSONAL).source(TransactionSource.CSV_IMPORT)
                .dedupHash("h" + UUID.randomUUID()).build());
    }

    @Test
    void reviewsTheCallersRowsAndUpdatesOnlyThoseThatChange() throws Exception {
        UUID accountId = account(memberA);
        // a grocery row wrongly stored as OTHER, and an already-correct transport row
        txn(memberA, accountId, "COLES 0234", "Food & Drink", "OTHER");
        txn(memberA, accountId, "UBER TRIP", "Transportation", "TRANSPORT");

        mockMvc.perform(post("/api/v1/transactions/recategorize").with(member(household, memberA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewed").value(2))
                .andExpect(jsonPath("$.changed").value(1));   // only the grocery row moved

        List<Transaction> rows = transactionRepository
                .findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(household, memberA);
        assertThat(rows)
                .filteredOn(t -> t.getDescription().equals("COLES 0234"))
                .allMatch(t -> t.getCanonicalCategory().equals("GROCERIES"));
    }

    @Test
    void neverTouchesAnotherMembersRows() throws Exception {
        txn(memberA, account(memberA), "COLES 0234", "Food & Drink", "OTHER");
        txn(memberB, account(memberB), "COLES 0234", "Food & Drink", "OTHER");

        mockMvc.perform(post("/api/v1/transactions/recategorize").with(member(household, memberA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewed").value(1));   // only memberA's row

        // memberB's row is untouched
        assertThat(transactionRepository
                .findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(household, memberB))
                .allMatch(t -> t.getCanonicalCategory().equals("OTHER"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/recategorize")).andExpect(status().isUnauthorized());
    }
}
