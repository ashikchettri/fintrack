package com.fintrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * JWT issuance settings. {@code privateKey} points at a PKCS#8 PEM and is
 * required outside the local profile (ADR 002); leave unset locally to get an
 * ephemeral generated keypair.
 */
@ConfigurationProperties(prefix = "fintrack.auth.jwt")
public record JwtProperties(
        Resource privateKey,
        @DefaultValue("fintrack-auth") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl
) {
}
