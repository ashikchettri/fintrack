package com.fintrack.finance.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AuthenticatedMemberTest {

    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900));
    }

    @Test
    void extractsAllHouseholdClaims() {
        UUID householdId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Jwt jwt = baseJwt()
                .claim("householdId", householdId.toString())
                .claim("memberId", memberId.toString())
                .claim("role", "ADULT")
                .build();

        AuthenticatedMember member = AuthenticatedMember.from(jwt);

        assertThat(member.userId()).isEqualTo(UUID.fromString(jwt.getSubject()));
        assertThat(member.householdId()).isEqualTo(householdId);
        assertThat(member.memberId()).isEqualTo(memberId);
        assertThat(member.role()).isEqualTo("ADULT");
    }

    @Test
    void missingHouseholdClaimIsAContractViolation() {
        Jwt jwt = baseJwt().claim("memberId", UUID.randomUUID().toString()).build();

        assertThatIllegalStateException()
                .isThrownBy(() -> AuthenticatedMember.from(jwt))
                .withMessageContaining("householdId");
    }
}
