package com.fintrack.finance.config;

import com.fintrack.finance.service.ai.AnthropicChatClient;
import com.fintrack.finance.service.ai.ClaudeCategorizer;
import com.fintrack.finance.service.ai.RestClientAnthropicChatClient;
import com.fintrack.finance.service.ai.RuleBasedCategorizer;
import com.fintrack.finance.service.ai.TransactionCategorizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires AI categorization when {@code finance.ai.categorization.enabled=true}
 * (ADR 009). When it isn't, none of this loads and {@link RuleBasedCategorizer}
 * is the sole {@link TransactionCategorizer} — so the app needs no API key.
 * Mirrors auth-service's email-provider-chain config.
 */
@Configuration
@ConditionalOnProperty(prefix = "finance.ai.categorization", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiCategorizationProperties.class)
public class AiCategorizationConfig {

    @Bean
    AnthropicChatClient anthropicChatClient(AiCategorizationProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "finance.ai.categorization.enabled=true but finance.ai.categorization.api-key is not set");
        }
        RestClient http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        return new RestClientAnthropicChatClient(http, props.model());
    }

    /** Takes precedence over the rule-based bean; the AI delegates to it as its fallback. */
    @Bean
    @Primary
    TransactionCategorizer claudeCategorizer(AnthropicChatClient client, RuleBasedCategorizer fallback,
                                             ObjectMapper mapper, AiCategorizationProperties props) {
        return new ClaudeCategorizer(client, fallback, mapper, props.batchSize());
    }
}
