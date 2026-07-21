package com.fintrack.insight.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * The caller's identity from the verified JWT (the claims auth-service planted).
 * insight-service doesn't query by these directly — it forwards the caller's
 * token to finance-service, which enforces household/member scoping — but the
 * claims identify the caller for logging and any local checks.
 */
public record AuthenticatedMember(UUID userId, UUID householdId, UUID memberId, String role) {

    public static AuthenticatedMember from(Jwt jwt) {
        return new AuthenticatedMember(
                UUID.fromString(jwt.getSubject()),
                requiredUuidClaim(jwt, "householdId"),
                requiredUuidClaim(jwt, "memberId"),
                jwt.getClaimAsString("role"));
    }

    private static UUID requiredUuidClaim(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        if (value == null) {
            throw new IllegalStateException("JWT is missing required claim: " + name);
        }
        return UUID.fromString(value);
    }
}
