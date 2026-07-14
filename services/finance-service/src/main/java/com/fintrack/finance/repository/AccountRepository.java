package com.fintrack.finance.repository;

import com.fintrack.finance.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // all reads are household + member scoped — a caller only ever sees their own
    List<Account> findByHouseholdIdAndMemberIdOrderByCreatedAtDesc(UUID householdId, UUID memberId);

    Optional<Account> findByIdAndHouseholdIdAndMemberId(UUID id, UUID householdId, UUID memberId);
}
