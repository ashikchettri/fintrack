package com.fintrack.auth.web.dto;

import com.fintrack.auth.service.SignupResult;

import java.time.Instant;
import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String email,
        UUID householdId,
        String householdName,
        String role,
        Instant createdAt
) {
    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(
                result.user().getId(),
                result.user().getEmail(),
                result.household().getId(),
                result.household().getName(),
                result.member().getRole().name(),
                result.user().getCreatedAt());
    }
}