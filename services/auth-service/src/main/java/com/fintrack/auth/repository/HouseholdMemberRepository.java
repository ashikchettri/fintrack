package com.fintrack.auth.repository;

import com.fintrack.auth.domain.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// query methods (findByUserId etc.) arrive with the login feature — YAGNI until then
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {
}