package com.fintrack.finance.service;

import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.DashboardResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private final AuthenticatedMember caller =
            new AuthenticatedMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER");

    private Transaction txn(LocalDate date, String desc, String category, String amount) {
        return Transaction.builder()
                .householdId(caller.householdId()).memberId(caller.memberId()).accountId(UUID.randomUUID())
                .txnDate(date).description(desc).category(category)
                .amount(new BigDecimal(amount)).currency("AUD").dedupHash("h" + UUID.randomUUID())
                .build();
    }

    @Test
    void aggregatesTotalsCategoriesMerchantsAndMonths() {
        // provided most-recent first, as the repository guarantees
        when(transactionRepository.findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(any(), any()))
                .thenReturn(List.of(
                        txn(LocalDate.of(2026, 7, 11), "Transport NSW", "Transportation", "-12.50"),
                        txn(LocalDate.of(2026, 6, 1), "Salary", "Income", "3000.00"),
                        txn(LocalDate.of(2026, 5, 26), "Reddy Express", "Transportation", "-60.00"),
                        txn(LocalDate.of(2026, 5, 26), "Reddy Express", "Transportation", "-60.00"),
                        txn(LocalDate.of(2026, 4, 14), "Woolworths", "Food & Drink", "-85.20"),
                        txn(LocalDate.of(2025, 8, 17), "SHEIN Refund", "Personal", "40.00")));

        DashboardResponse d = dashboardFor();

        assertThat(d.currency()).isEqualTo("AUD");
        assertThat(d.totals().income()).isEqualByComparingTo("3040.00");
        assertThat(d.totals().expenses()).isEqualByComparingTo("217.70");
        assertThat(d.totals().net()).isEqualByComparingTo("2822.30");
        assertThat(d.totals().transactionCount()).isEqualTo(6);

        // categories sorted by spend, with share of total spend
        assertThat(d.byCategory()).hasSize(2);
        assertThat(d.byCategory().get(0).category()).isEqualTo("Transportation");
        assertThat(d.byCategory().get(0).spent()).isEqualByComparingTo("132.50");
        assertThat(d.byCategory().get(0).share()).isEqualTo(0.6086);

        // Reddy Express is the top merchant (2 × 60)
        assertThat(d.topMerchants().get(0).description()).isEqualTo("Reddy Express");
        assertThat(d.topMerchants().get(0).spent()).isEqualByComparingTo("120.00");
        assertThat(d.topMerchants().get(0).count()).isEqualTo(2);

        // months ascending; income lands only in June
        assertThat(d.byMonth()).extracting(DashboardResponse.MonthlyFlow::month)
                .containsExactly("2025-08", "2026-04", "2026-05", "2026-06", "2026-07");
        assertThat(d.byMonth().get(3).income()).isEqualByComparingTo("3000.00");

        // most-recent first, capped
        assertThat(d.recent().get(0).description()).isEqualTo("Transport NSW");
    }

    @Test
    void emptyMemberGetsAZeroedDashboard() {
        when(transactionRepository.findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(any(), any()))
                .thenReturn(List.of());

        DashboardResponse d = dashboardFor();

        assertThat(d.currency()).isNull();
        assertThat(d.totals().net()).isEqualByComparingTo("0");
        assertThat(d.totals().transactionCount()).isZero();
        assertThat(d.byCategory()).isEmpty();
        assertThat(d.recent()).isEmpty();
    }

    private DashboardResponse dashboardFor() {
        return new DashboardService(transactionRepository).build(caller);
    }
}
