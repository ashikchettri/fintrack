package com.fintrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * One-time-code knobs shared by signup verification and password reset
 * (ADR 004/005, unified to 6 digits on 2026-07-13). The keyspace is
 * compensated by codeTtl + maxAttempts either way.
 */
@ConfigurationProperties(prefix = "fintrack.auth.verification")
public record VerificationProperties(
        @DefaultValue("6") int codeLength,
        @DefaultValue("15m") Duration codeTtl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("60s") Duration resendCooldown,
        @DefaultValue("no-reply@fintrack.local") String fromAddress
) {
}
