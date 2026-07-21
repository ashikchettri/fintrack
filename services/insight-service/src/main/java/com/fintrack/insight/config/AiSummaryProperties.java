package com.fintrack.insight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI summary knobs (ADR 012). Off by default: with {@code enabled=false} the
 * service runs on the template generator and needs no API key.
 */
@ConfigurationProperties(prefix = "insight.ai")
public record AiSummaryProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("claude-haiku-4-5-20251001") String model,
        @DefaultValue("https://api.anthropic.com") String baseUrl
) {
}
