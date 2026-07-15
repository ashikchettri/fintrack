package com.fintrack.finance.service;

import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.TransactionSource;
import com.fintrack.finance.domain.Visibility;
import com.fintrack.finance.repository.TransactionRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.SharedHouseholdView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedCommitmentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private final UUID household = UUID.randomUUID();
    private final UUID meId = UUID.randomUUID();
    private final UUID partnerId = UUID.randomUUID();
    private final AuthenticatedMember me =
            new AuthenticatedMember(UUID.randomUUID(), household, meId, "OWNER");

    private SharedCommitmentService service() {
        return new SharedCommitmentService(transactionRepository);
    }

    private Transaction shared(UUID member, String category, String amount) {
        return sharedOn(member, category, amount, LocalDate.of(2026, 7, 1));
    }

    private Transaction sharedOn(UUID member, String category, String amount, LocalDate date) {
        return Transaction.builder()
                .householdId(household).memberId(member).accountId(UUID.randomUUID())
                .txnDate(date).description(category).category(category)
                .amount(new BigDecimal(amount)).currency("AUD")
                .visibility(Visibility.SHARED).source(TransactionSource.MANUAL)
                .dedupHash("h" + UUID.randomUUID()).build();
    }

    @Test
    void setVisibilityMarksTheCallersOwnTransaction() {
        UUID id = UUID.randomUUID();
        Transaction owned = shared(meId, "Rent", "-1000.00");
        when(transactionRepository.findByIdAndHouseholdIdAndMemberId(id, household, meId))
                .thenReturn(Optional.of(owned));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().setVisibility(me, id, Visibility.SHARED);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.captor();
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getVisibility()).isEqualTo(Visibility.SHARED);
    }

    @Test
    void markingSomeoneElsesTransactionIs404() {
        when(transactionRepository.findByIdAndHouseholdIdAndMemberId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(TransactionNotFoundException.class)
                .isThrownBy(() -> service().setVisibility(me, UUID.randomUUID(), Visibility.SHARED));
    }

    @Test
    void sharedViewSplitsEquallyAndSettlesFromTheCallersPerspective() {
        // me covered 1200 (rent 1000 + groceries 200); partner covered 400 (utilities)
        when(transactionRepository.findByHouseholdIdAndVisibilityOrderByTxnDateDescCreatedAtDesc(
                household, Visibility.SHARED))
                .thenReturn(List.of(
                        shared(meId, "Housing", "-1000.00"),
                        shared(meId, "Groceries", "-200.00"),
                        shared(partnerId, "Utilities", "-400.00")));

        SharedHouseholdView view = service().sharedView(me, null);

        assertThat(view.totalShared()).isEqualByComparingTo("1600.00");
        assertThat(view.memberCount()).isEqualTo(2);
        assertThat(view.fairShare()).isEqualByComparingTo("800.00");

        // I covered 1200, fair share 800 → I'm owed 400
        assertThat(view.settlement().yourContribution()).isEqualByComparingTo("1200.00");
        assertThat(view.settlement().status()).isEqualTo("owed");
        assertThat(view.settlement().amount()).isEqualByComparingTo("400.00");

        // contributions sorted by covered desc, "you" flagged
        assertThat(view.contributions()).hasSize(2);
        assertThat(view.contributions().get(0).memberId()).isEqualTo(meId);
        assertThat(view.contributions().get(0).isYou()).isTrue();
        assertThat(view.contributions().get(1).covered()).isEqualByComparingTo("400.00");

        // category breakdown of shared spend
        assertThat(view.byCategory().get(0).category()).isEqualTo("Housing");
        assertThat(view.transactions()).hasSize(3);
    }

    @Test
    void theUnderpayerOwesTheirShortfall() {
        // from the partner's perspective, who covered only 400 of an 800 fair share
        AuthenticatedMember partner = new AuthenticatedMember(UUID.randomUUID(), household, partnerId, "ADULT");
        when(transactionRepository.findByHouseholdIdAndVisibilityOrderByTxnDateDescCreatedAtDesc(
                household, Visibility.SHARED))
                .thenReturn(List.of(
                        shared(meId, "Housing", "-1200.00"),
                        shared(partnerId, "Utilities", "-400.00")));

        SharedHouseholdView view = service().sharedView(partner, null);

        assertThat(view.fairShare()).isEqualByComparingTo("800.00");
        assertThat(view.settlement().status()).isEqualTo("owes");
        assertThat(view.settlement().amount()).isEqualByComparingTo("400.00");
    }

    @Test
    void sharedViewCanBeScopedToOneMonth() {
        when(transactionRepository.findByHouseholdIdAndVisibilityOrderByTxnDateDescCreatedAtDesc(
                household, Visibility.SHARED))
                .thenReturn(List.of(
                        sharedOn(meId, "Rent", "-1000.00", LocalDate.of(2026, 7, 1)),
                        sharedOn(partnerId, "Utilities", "-400.00", LocalDate.of(2026, 6, 1))));

        SharedHouseholdView view = service().sharedView(me, YearMonth.of(2026, 7));

        assertThat(view.month()).isEqualTo("2026-07");
        assertThat(view.totalShared()).isEqualByComparingTo("1000.00");   // only July's rent
        assertThat(view.memberCount()).isEqualTo(1);
        // the selector still lists every month that has shared activity
        assertThat(view.availableMonths()).containsExactly("2026-07", "2026-06");
    }

    @Test
    void anEmptyHouseholdViewIsAllZeroNotAnError() {
        when(transactionRepository.findByHouseholdIdAndVisibilityOrderByTxnDateDescCreatedAtDesc(
                household, Visibility.SHARED))
                .thenReturn(List.of());

        SharedHouseholdView view = service().sharedView(me, null);

        assertThat(view.totalShared()).isEqualByComparingTo("0");
        assertThat(view.memberCount()).isZero();
        assertThat(view.settlement().status()).isEqualTo("settled");
        assertThat(view.contributions()).isEmpty();
        assertThat(view.currency()).isNull();
    }
}
