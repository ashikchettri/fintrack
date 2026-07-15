package com.fintrack.finance.repository;

import com.fintrack.finance.domain.Income;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    // a member's own income (one per member per household)
    Optional<Income> findByHouseholdIdAndMemberId(UUID householdId, UUID memberId);

    // every member's income — for the household total
    List<Income> findByHouseholdId(UUID householdId);
}
