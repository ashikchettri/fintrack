package com.fintrack.auth.web.dto;

import com.fintrack.auth.service.UserProfileService.UserProfile;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID userId,
        String email,
        UUID householdId,
        String householdName,
        String role,
        Instant createdAt
) {
    public static UserResponse from(UserProfile profile) {
        return new UserResponse(
                profile.user().getId(),
                profile.user().getEmail(),
                profile.member().getHousehold().getId(),
                profile.member().getHousehold().getName(),
                profile.member().getRole().name(),
                profile.user().getCreatedAt());
    }
}
