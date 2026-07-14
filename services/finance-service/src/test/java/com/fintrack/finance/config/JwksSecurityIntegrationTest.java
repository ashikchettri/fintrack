package com.fintrack.finance.config;

import com.fintrack.finance.TestcontainersConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the cross-service trust chain end to end: this service accepts ONLY
 * tokens whose signature matches the JWKS it fetched — exactly how it will
 * trust auth-service in production, here with a stub JWKS server (plain JDK
 * HttpServer, no extra dependencies).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwksSecurityIntegrationTest {

    private static RSAKey signingKey;   // "auth-service's" key — published in JWKS
    private static RSAKey rogueKey;     // attacker's key — NOT in JWKS
    private static HttpServer jwksServer;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startJwksServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("auth-key").generate();
        rogueKey = new RSAKeyGenerator(2048).keyID("rogue-key").generate();

        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            byte[] body = new JWKSet(signingKey.toPublicJWK()).toString()
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    @DynamicPropertySource
    static void jwksUri(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + jwksServer.getAddress().getPort()
                        + "/.well-known/jwks.json");
    }

    private static String mintToken(RSAKey key, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("fintrack-auth")
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .claim("householdId", UUID.randomUUID().toString())
                .claim("memberId", UUID.randomUUID().toString())
                .claim("role", "OWNER")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/security-probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByTheJwksKeyIsAccepted() throws Exception {
        String token = mintToken(signingKey, Instant.now().plusSeconds(900));

        // authenticated → past security → 404 because no handler exists yet
        mockMvc.perform(get("/api/v1/security-probe").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenSignedByAnUnknownKeyIsRejected() throws Exception {
        // right shape, right claims, wrong key — the signature check is the trust
        String token = mintToken(rogueKey, Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/security-probe").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = mintToken(signingKey, Instant.now().minusSeconds(120));

        mockMvc.perform(get("/api/v1/security-probe").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/security-probe").header(AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesStayPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
