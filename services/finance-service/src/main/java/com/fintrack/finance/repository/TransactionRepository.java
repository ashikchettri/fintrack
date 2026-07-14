package com.fintrack.finance.repository;

import com.fintrack.finance.domain.Transaction;
import com.fintrack.finance.domain.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // scoped feed for the dashboard + transactions list — a caller only ever
    // sees their own rows, most-recent first
    List<Transaction> findByHouseholdIdAndMemberIdOrderByTxnDateDescCreatedAtDesc(
            UUID householdId, UUID memberId);

    // member-scoped single row — for the "mark as shared" action, so a member
    // can only change the visibility of their OWN transaction
    Optional<Transaction> findByIdAndHouseholdIdAndMemberId(UUID id, UUID householdId, UUID memberId);

    // the privacy boundary (ADR 006): a household's SHARED rows across every
    // member; personal rows never match this predicate, so they're unreachable
    List<Transaction> findByHouseholdIdAndVisibilityOrderByTxnDateDescCreatedAtDesc(
            UUID householdId, Visibility visibility);

    /**
     * The member's existing natural-key digests, fetched once per import so the
     * whole batch dedups in memory instead of a round-trip per row.
     */
    @Query("select t.dedupHash from Transaction t "
            + "where t.householdId = :householdId and t.memberId = :memberId")
    Set<String> findDedupHashes(@Param("householdId") UUID householdId,
                                @Param("memberId") UUID memberId);
}
