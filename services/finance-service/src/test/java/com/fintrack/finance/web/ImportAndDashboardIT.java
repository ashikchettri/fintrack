package com.fintrack.finance.web;

import com.fintrack.finance.TestcontainersConfiguration;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The hero flow end-to-end over real Postgres: upload a bank CSV, get a summary,
 * see the dashboard populate — and prove a re-upload dedups and that one
 * member's import never reaches another (ADR 001 / household scoping).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportAndDashboardIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;

    @AfterEach
    void cleanup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private static RequestPostProcessor member(UUID householdId, UUID memberId) {
        return jwt().jwt(builder -> builder
                        .subject(UUID.randomUUID().toString())
                        .claim("householdId", householdId.toString())
                        .claim("memberId", memberId.toString())
                        .claim("role", "OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private static MockMultipartFile sampleCsv() throws IOException {
        byte[] bytes = new ClassPathResource("import/sample-transactions.csv").getContentAsByteArray();
        return new MockMultipartFile("file", "sample-transactions.csv", "text/csv", bytes);
    }

    @Test
    void uploadImportsRowsCreatesAccountsAndReportsSkips() throws Exception {
        UUID household = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/imports/transactions").file(sampleCsv())
                        .param("currency", "AUD").with(member(household, memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(6))          // 7 rows, 1 malformed skipped
                .andExpect(jsonPath("$.duplicatesSkipped").value(0))
                .andExpect(jsonPath("$.failedRows").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.accountsCreated.length()").value(2))   // Everyday + Savings
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.totalIncome").value(3040.0))          // 3000 salary + 40 refund
                .andExpect(jsonPath("$.totalExpenses").value(217.7))
                .andExpect(jsonPath("$.net").value(2822.3));
    }

    @Test
    void reuploadingTheSameStatementDedupsInsteadOfDoubleCounting() throws Exception {
        UUID household = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/imports/transactions").file(sampleCsv())
                        .param("currency", "AUD").with(member(household, memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(6));

        // second upload of the identical file: everything is a known duplicate
        mockMvc.perform(multipart("/api/v1/imports/transactions").file(sampleCsv())
                        .param("currency", "AUD").with(member(household, memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicatesSkipped").value(6))
                .andExpect(jsonPath("$.accountsCreated.length()").value(0));  // accounts already exist
    }

    @Test
    void dashboardReflectsTheImportAndIsScopedToTheMember() throws Exception {
        UUID household = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/imports/transactions").file(sampleCsv())
                        .param("currency", "AUD").with(member(household, memberId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/dashboard").with(member(household, memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.totals.transactionCount").value(6))
                .andExpect(jsonPath("$.totals.income").value(3040.0))
                .andExpect(jsonPath("$.totals.expenses").value(217.7))
                .andExpect(jsonPath("$.byCategory[0].category").value("Transportation"))   // biggest spend
                .andExpect(jsonPath("$.byCategory[0].spent").value(132.5))
                .andExpect(jsonPath("$.topMerchants[0].description").value("Reddy Express"))
                .andExpect(jsonPath("$.topMerchants[0].count").value(2))
                .andExpect(jsonPath("$.recent.length()").value(6));

        mockMvc.perform(get("/api/v1/transactions").with(member(household, memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        // a different household sees nothing — the import never leaked
        UUID otherHousehold = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/dashboard").with(member(otherHousehold, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.transactionCount").value(0))
                .andExpect(jsonPath("$.recent.length()").value(0));

        mockMvc.perform(get("/api/v1/transactions").with(member(otherHousehold, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aFileMissingRequiredColumnsIs400() throws Exception {
        MockMultipartFile bad = new MockMultipartFile("file", "bad.csv", "text/csv",
                "Date,Description\n2026-01-01,Nope\n".getBytes());

        mockMvc.perform(multipart("/api/v1/imports/transactions").file(bad)
                        .with(member(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("CSV import failed"));
    }

    @Test
    void importWithoutATokenIs401() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/transactions").file(sampleCsv()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
