package com.fintrack.insight.config;

import com.fintrack.insight.service.SummaryGenerator;
import com.fintrack.insight.service.TemplateSummaryGenerator;
import com.fintrack.insight.service.ai.AnthropicChatClient;
import com.fintrack.insight.service.ai.ClaudeSummaryGenerator;
import com.fintrack.insight.service.ai.RestClientAnthropicChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires AI summaries when {@code insight.ai.enabled=true} (ADR 012). Otherwise
 * none of this loads and {@link TemplateSummaryGenerator} is the sole
 * {@link SummaryGenerator} — so the service needs no API key.
 */
@Configuration
@ConditionalOnProperty(prefix = "insight.ai", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiSummaryProperties.class)
public class AiSummaryConfig {

    @Bean
    AnthropicChatClient anthropicChatClient(AiSummaryProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("insight.ai.enabled=true but insight.ai.api-key is not set");
        }
        RestClient http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        return new RestClientAnthropicChatClient(http, props.model());
    }

    @Bean
    @Primary
    SummaryGenerator claudeSummaryGenerator(AnthropicChatClient client,
                                            TemplateSummaryGenerator fallback,
                                            ObjectMapper mapper) {
        return new ClaudeSummaryGenerator(client, fallback, mapper);
    }
}
