package com.fintrack.finance.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * The caller's identity as carried by the verified JWT — the claims planted by
 * auth-service (ARCHITECTURE §4) so this service authorizes without callbacks.
 * Every household-scoped query MUST filter by these values, never by
 * client-supplied parameters.
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
            // a verified token without household claims is a contract violation,
            // not a client error — fail loudly
            throw new IllegalStateException("JWT is missing required claim: " + name);
        }
        return UUID.fromString(value);
    }
}
