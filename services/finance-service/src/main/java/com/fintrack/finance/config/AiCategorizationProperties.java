package com.fintrack.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI categorization knobs (ADR 009). Off by default: with {@code enabled=false}
 * the app runs on the rule-based categorizer and needs no API key.
 */
@ConfigurationProperties(prefix = "finance.ai.categorization")
public record AiCategorizationProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("claude-haiku-4-5-20251001") String model,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("50") int batchSize
) {
}
