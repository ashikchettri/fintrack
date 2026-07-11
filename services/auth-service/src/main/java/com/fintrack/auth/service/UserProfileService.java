package com.fintrack.auth.service;

import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    public UserProfileService(UserRepository userRepository,
                              HouseholdMemberRepository householdMemberRepository) {
        this.userRepository = userRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    public record UserProfile(User user, HouseholdMember member) {
    }

    /**
     * Caller identity comes from a verified JWT, so absence of the user or
     * membership is data corruption (accounts can't be deleted in phase 1),
     * not an auth failure.
     */
    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "no user for authenticated subject %s".formatted(userId)));
        HouseholdMember member = householdMemberRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "user %s has no household membership".formatted(userId)));
        return new UserProfile(user, member);
    }
}
