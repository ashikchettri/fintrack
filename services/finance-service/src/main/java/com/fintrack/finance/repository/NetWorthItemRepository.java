package com.fintrack.finance.repository;

import com.fintrack.finance.domain.NetWorthItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NetWorthItemRepository extends JpaRepository<NetWorthItem, UUID> {

    List<NetWorthItem> findByHouseholdIdOrderBySortOrder(UUID householdId);

    @Modifying
    @Query("delete from NetWorthItem i where i.householdId = ?1")
    void deleteByHouseholdId(UUID householdId);
}
