package com.fintrack.finance.service;

import com.fintrack.finance.domain.HomeLoan;
import com.fintrack.finance.domain.NetWorthItem;
import com.fintrack.finance.domain.NetWorthKind;
import com.fintrack.finance.repository.HomeLoanRepository;
import com.fintrack.finance.repository.NetWorthItemRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.NetWorthItemDto;
import com.fintrack.finance.web.dto.NetWorthRequest;
import com.fintrack.finance.web.dto.NetWorthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetWorthServiceTest {

    @Mock
    private NetWorthItemRepository itemRepository;
    @Mock
    private HomeLoanRepository homeLoanRepository;

    private NetWorthService service;
    private AuthenticatedMember caller;

    @BeforeEach
    void setUp() {
        service = new NetWorthService(itemRepository, homeLoanRepository);
        caller = new AuthenticatedMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER");
    }

    private NetWorthItem item(NetWorthKind kind, String category, String name, String value) {
        return new NetWorthItem(caller.householdId(), kind, category, name, new BigDecimal(value), 0, "AUD");
    }

    @Test
    void foldsTheHomeLoanIntoTheSummaryAndComputesNetWorth() {
        when(itemRepository.findByHouseholdIdOrderBySortOrder(caller.householdId())).thenReturn(List.of(
                item(NetWorthKind.ASSET, "Property", "Home", "800000"),
                item(NetWorthKind.LIABILITY, "Credit card", "Visa", "3000")));

        HomeLoan loan = mock(HomeLoan.class);
        when(loan.isHasHomeLoan()).thenReturn(true);
        when(loan.getLoanAmount()).thenReturn(new BigDecimal("500000"));
        when(loan.isHasOffset()).thenReturn(true);
        when(loan.getOffsetBalance()).thenReturn(new BigDecimal("120000"));
        when(homeLoanRepository.findByHouseholdId(caller.householdId())).thenReturn(Optional.of(loan));

        NetWorthResponse summary = service.summary(caller);

        // assets: Home 800k (manual) + Offset 120k (home loan) = 920k
        assertThat(summary.totalAssets()).isEqualByComparingTo("920000");
        // liabilities: Visa 3k (manual) + Home loan 500k (home loan) = 503k
        assertThat(summary.totalLiabilities()).isEqualByComparingTo("503000");
        assertThat(summary.netWorth()).isEqualByComparingTo("417000");

        assertThat(summary.assets()).extracting(NetWorthResponse.Line::source)
                .containsExactly("MANUAL", "HOME_LOAN");
        assertThat(summary.liabilities()).extracting(NetWorthResponse.Line::name)
                .contains("Visa", "Home loan");
    }

    @Test
    void worksWithNoHomeLoanAndNoItems() {
        when(itemRepository.findByHouseholdIdOrderBySortOrder(caller.householdId())).thenReturn(List.of());
        when(homeLoanRepository.findByHouseholdId(caller.householdId())).thenReturn(Optional.empty());

        NetWorthResponse summary = service.summary(caller);

        assertThat(summary.netWorth()).isEqualByComparingTo("0");
        assertThat(summary.assets()).isEmpty();
        assertThat(summary.liabilities()).isEmpty();
    }

    @Test
    void saveReplacesAllAndDropsBlankRows() {
        NetWorthRequest req = new NetWorthRequest("aud", List.of(
                new NetWorthItemDto(NetWorthKind.ASSET, "Savings & cash", "Offset", new BigDecimal("50000")),
                new NetWorthItemDto(NetWorthKind.LIABILITY, null, "  ", new BigDecimal("1")),   // blank name → dropped
                new NetWorthItemDto(NetWorthKind.LIABILITY, "Car loan", "Ute", new BigDecimal("18000"))));

        service.save(caller, req);

        verify(itemRepository).deleteByHouseholdId(caller.householdId());
        ArgumentCaptor<List<NetWorthItem>> saved = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(NetWorthItem::getName).containsExactly("Offset", "Ute");
        // currency is normalised to upper case
        assertThat(saved.getValue().get(0).getCurrency()).isEqualTo("AUD");
    }
}
