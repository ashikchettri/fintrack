package com.fintrack.finance.repository;

import com.fintrack.finance.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // scoped feed for the dashboard + transactions list — a caller only ever
    // sees their own rows, most-recent first
    List<Transaction> findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
            UUID householdId, UUID memberId);

    /**
     * The member's existing natural-key digests, fetched once per import so the
     * whole batch dedups in memory instead of a round-trip per row.
     */
    @Query("select t.dedupHash from Transaction t "
            + "where t.householdId = :householdId and t.memberId = :memberId")
    Set<String> findDedupHashes(@Param("householdId") UUID householdId,
                                @Param("memberId") UUID memberId);
}
