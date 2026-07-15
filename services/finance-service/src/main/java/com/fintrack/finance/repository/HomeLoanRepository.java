package com.fintrack.finance.repository;

import com.fintrack.finance.domain.HomeLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HomeLoanRepository extends JpaRepository<HomeLoan, UUID> {

    // one profile per household (jointly held) — not member-scoped
    Optional<HomeLoan> findByHouseholdId(UUID householdId);
}
