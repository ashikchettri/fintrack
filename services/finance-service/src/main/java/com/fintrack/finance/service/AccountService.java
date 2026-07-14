package com.fintrack.finance.service;

import com.fintrack.finance.domain.Account;
import com.fintrack.finance.domain.AccountType;
import com.fintrack.finance.repository.AccountRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Accounts CRUD. Every operation is scoped to the caller's household + member
 * (from the verified JWT via {@link AuthenticatedMember}) — never a
 * client-supplied id — so a caller can only ever touch their own accounts.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account create(AuthenticatedMember caller, String name, AccountType type,
                          String currency, BigDecimal openingBalance) {
        Account account = new Account(
                caller.householdId(),
                caller.memberId(),
                name.strip(),
                type,
                currency.toUpperCase(Locale.ROOT),
                openingBalance);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Account> list(AuthenticatedMember caller) {
        return accountRepository.findByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                caller.householdId(), caller.memberId());
    }

    @Transactional(readOnly = true)
    public Account get(AuthenticatedMember caller, UUID id) {
        return requireOwned(caller, id);
    }

    @Transactional
    public void delete(AuthenticatedMember caller, UUID id) {
        // scoped lookup so deleting a not-yours id is a 404, not a silent no-op.
        // Uses the private helper (not get()) to avoid a @Transactional
        // self-invocation, which would bypass the proxy (Sonar S6809).
        accountRepository.delete(requireOwned(caller, id));
    }

    private Account requireOwned(AuthenticatedMember caller, UUID id) {
        return accountRepository
                .findByIdAndHouseholdIdAndMemberId(id, caller.householdId(), caller.memberId())
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
