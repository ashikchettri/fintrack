package com.fintrack.finance.repository;

import com.fintrack.finance.domain.BudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {

    List<BudgetLine> findByHouseholdIdOrderBySortOrder(UUID householdId);

    // budget saves are replace-all: clear the household's lines, then insert
    @Modifying
    @Query("delete from BudgetLine b where b.householdId = ?1")
    void deleteByHouseholdId(UUID householdId);
}
