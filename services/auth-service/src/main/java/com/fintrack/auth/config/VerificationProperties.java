package com.fintrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Email-verification knobs (ADR 004). 4-digit default per product decision;
 * the small keyspace is compensated by codeTtl + maxAttempts. Bump
 * codeLength to 6 when the stakes rise — one property, no code change.
 */
@ConfigurationProperties(prefix = "fintrack.auth.verification")
public record VerificationProperties(
        @DefaultValue("4") int codeLength,
        @DefaultValue("15m") Duration codeTtl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("60s") Duration resendCooldown,
        @DefaultValue("no-reply@fintrack.local") String fromAddress
) {
}
