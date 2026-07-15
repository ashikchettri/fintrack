package com.fintrack.auth.repository;

import com.fintrack.auth.domain.HouseholdInvite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdInviteRepository extends JpaRepository<HouseholdInvite, UUID> {

    // accept resolves the newest invite for the email, then checks it's still live;
    // household is fetched eagerly so the membership can be created after the tx
    @EntityGraph(attributePaths = "household")
    Optional<HouseholdInvite> findFirstByEmailOrderByCreatedAtDesc(String email);

    // re-inviting the same email replaces any prior pending invite for that household
    @Modifying
    @Query("delete from HouseholdInvite i where i.household.id = :householdId and i.email = :email")
    void deleteByHouseholdAndEmail(@Param("householdId") UUID householdId, @Param("email") String email);
}
