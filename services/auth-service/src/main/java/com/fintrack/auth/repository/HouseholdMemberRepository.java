package com.fintrack.auth.repository;

import com.fintrack.auth.domain.HouseholdMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    // EntityGraph fetches user+household in the same query: with open-in-view=false,
    // lazy proxies die once the transaction ends (found the hard way via /users/me),
    // and every caller reads both associations anyway.
    @EntityGraph(attributePaths = {"user", "household"})
    Optional<HouseholdMember> findByUserId(UUID userId);

    // every member of a household — for the household roster (names + roles).
    // user is fetched for the display-name fallback (email local part)
    @EntityGraph(attributePaths = "user")
    List<HouseholdMember> findByHouseholdIdOrderByCreatedAt(UUID householdId);
}
