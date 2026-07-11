package com.fintrack.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The three ADR 002 behaviors: load a configured PEM (any profile),
 * generate ephemerally in local, fail fast anywhere else.
 */
class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    private static JwtProperties propsWithKey(ByteArrayResource pem) {
        return new JwtProperties(pem, "fintrack-auth", Duration.ofMinutes(15), Duration.ofDays(7));
    }

    private static MockEnvironment envWithProfile(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env;
    }

    @Test
    void configuredPemIsLoadedRegardlessOfProfile() throws Exception {
        KeyPair generated = rsaKeyPair();
        String pem = toPkcs8Pem(generated);

        RSAKey key = config.rsaSigningKey(
                propsWithKey(new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8))),
                envWithProfile("k8s"));

        // same key material in, same key material out
        assertThat(key.toRSAPublicKey().getModulus())
                .isEqualTo(((RSAPublicKey) generated.getPublic()).getModulus());
        // kid is the RFC 7638 thumbprint — deterministic for the same key
        assertThat(key.getKeyID()).isEqualTo(key.computeThumbprint().toString());
    }

    @Test
    void localProfileWithoutKeyGeneratesEphemeralKeypair() throws Exception {
        RSAKey key = config.rsaSigningKey(propsWithKey(null), envWithProfile("local"));

        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyID()).isNotBlank();
    }

    @Test
    void nonLocalProfileWithoutKeyFailsAtStartup() {
        assertThatIllegalStateException()
                .isThrownBy(() -> config.rsaSigningKey(propsWithKey(null), envWithProfile("gcp")))
                .withMessageContaining("ADR 002");
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPkcs8Pem(KeyPair keyPair) {
        // gitleaks:allow — PEM *template* around a key generated at test runtime;
        // no key material exists in this file
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }
}
