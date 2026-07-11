package com.fintrack.auth.repository;

import com.fintrack.auth.domain.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    // phase 1: one membership per user; becomes a list when invitations land (phase 7)
    Optional<HouseholdMember> findByUserId(UUID userId);
}
