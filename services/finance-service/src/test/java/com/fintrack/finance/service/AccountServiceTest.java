package com.fintrack.finance.service;

import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService service;
    private AuthenticatedMember caller;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository);
        caller = new AuthenticatedMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER");
    }

    @Test
    void createStampsHouseholdAndMemberFromTheCallerAndNormalizesInput() {
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account account = service.create(caller, "  Everyday  ", AccountType.CHECKING,
                "aud", new BigDecimal("100.5000"));

        // scope is taken from the JWT, never from input
        assertThat(account.getHouseholdId()).isEqualTo(caller.householdId());
        assertThat(account.getMemberId()).isEqualTo(caller.memberId());
        assertThat(account.getName()).isEqualTo("Everyday");   // trimmed
        assertThat(account.getCurrency()).isEqualTo("AUD");     // upper-cased
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("100.5");
        verify(accountRepository).save(account);
    }

    @Test
    void getScopesByHouseholdAndMember() {
        UUID id = UUID.randomUUID();
        Account owned = new Account(caller.householdId(), caller.memberId(),
                "A", AccountType.SAVINGS, "AUD", BigDecimal.ZERO);
        when(accountRepository.findByIdAndHouseholdIdAndMemberId(id, caller.householdId(), caller.memberId()))
                .thenReturn(Optional.of(owned));

        assertThat(service.get(caller, id)).isSameAs(owned);
    }

    @Test
    void getSomeoneElsesAccountIs404() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndHouseholdIdAndMemberId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.get(caller, id));
    }

    @Test
    void deleteLoadsScopedFirstSoNotYoursIsA404() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndHouseholdIdAndMemberId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.delete(caller, id));
        verify(accountRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesAnOwnedAccount() {
        UUID id = UUID.randomUUID();
        Account owned = new Account(caller.householdId(), caller.memberId(),
                "A", AccountType.CASH, "AUD", BigDecimal.ZERO);
        when(accountRepository.findByIdAndHouseholdIdAndMemberId(id, caller.householdId(), caller.memberId()))
                .thenReturn(Optional.of(owned));

        service.delete(caller, id);

        ArgumentCaptor<Account> deleted = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).delete(deleted.capture());
        assertThat(deleted.getValue()).isSameAs(owned);
    }

    @Test
    void listScopesByHouseholdAndMember() {
        service.list(caller);
        verify(accountRepository)
                .findByHouseholdIdAndMemberIdOrderByCreatedAtDesc(caller.householdId(), caller.memberId());
    }
}
