package com.fintrack.auth.repository;

import com.fintrack.auth.domain.HouseholdMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    // phase 1: one membership per user; becomes a list when invitations land (phase 7).
    // EntityGraph fetches user+household in the same query: with open-in-view=false,
    // lazy proxies die once the transaction ends (found the hard way via /users/me),
    // and every caller reads both associations anyway.
    @EntityGraph(attributePaths = {"user", "household"})
    Optional<HouseholdMember> findByUserId(UUID userId);
}
