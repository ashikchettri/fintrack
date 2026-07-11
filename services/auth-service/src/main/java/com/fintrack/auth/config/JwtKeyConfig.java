package com.fintrack.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * RS256 signing key wiring — see ADR 002.
 *
 * One loading path for all profiles (PKCS#8 PEM via fintrack.auth.jwt.private-key);
 * ephemeral generation is a local-profile-only fallback; anything else without a
 * configured key fails at startup, because a silently generated key in a
 * multi-replica deployment breaks verification randomly.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey rsaSigningKey(JwtProperties properties, Environment environment)
            throws GeneralSecurityException, JOSEException {
        KeyPair keyPair;
        if (properties.privateKey() != null) {
            keyPair = loadPkcs8Pem(properties.privateKey());
            log.info("Loaded JWT signing key from {}", properties.privateKey().getDescription());
        } else if (environment.matchesProfiles("local")) {
            log.warn("No JWT signing key configured — generating an EPHEMERAL keypair "
                    + "(local profile only). Access tokens will not survive a restart.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keyPair = generator.generateKeyPair();
        } else {
            throw new IllegalStateException(
                    "fintrack.auth.jwt.private-key must be configured outside the local profile (ADR 002)");
        }

        RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .build();
        // kid = RFC 7638 thumbprint: derived from the key material itself, so it is
        // stable across restarts for the same key and needs no configuration
        return new RSAKey.Builder(key)
                .keyID(key.computeThumbprint().toString())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaSigningKey)));
    }

    // Local verification for our own resource-server side; other services will
    // hit the JWKS endpoint instead of getting a decoder bean like this.
    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaSigningKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(rsaSigningKey.toRSAPublicKey()).build();
    }

    private KeyPair loadPkcs8Pem(Resource resource) throws GeneralSecurityException {
        String pem;
        try {
            pem = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read JWT signing key: " + resource.getDescription(), e);
        }
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey =
                (RSAPrivateCrtKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        // the public key is derivable from the private CRT key — one PEM is enough
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }
}
