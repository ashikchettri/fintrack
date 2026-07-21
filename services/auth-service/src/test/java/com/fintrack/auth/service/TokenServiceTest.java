package com.fintrack.auth.service;

import com.fintrack.auth.config.JwtProperties;
import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.domain.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a real Nimbus encoder with a throwaway key — mocking the JWT library
 * would only test our mocks. Refresh-token issuance now lives in
 * {@link JpaRefreshTokenStoreTest}.
 */
class TokenServiceTest {

    private static RSAKey testKey;

    private TokenService tokenService;
    private HouseholdMember member;

    @BeforeAll
    static void generateKey() throws Exception {
        testKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
    }

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                null, "test-issuer", Duration.ofMinutes(15), Duration.ofDays(7));
        tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(testKey))), properties);
        member = new HouseholdMember(
                new Household("jane's household"),
                new User("jane@example.com", "<hash>"),
                HouseholdRole.OWNER);
    }

    @Test
    void accessTokenCarriesHouseholdClaimsAndExpiry() throws Exception {
        String token = tokenService.issueAccessToken(member);

        Jwt jwt = NimbusJwtDecoder.withPublicKey(testKey.toRSAPublicKey()).build().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(member.getUser().getId().toString());
        assertThat(jwt.getClaimAsString("householdId"))
                .isEqualTo(member.getHousehold().getId().toString());
        assertThat(jwt.getClaimAsString("memberId")).isEqualTo(member.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
        // PII stays out of tokens — profile data belongs to /users/me
        assertThat(jwt.getClaims()).doesNotContainKey("email");
    }
}
