package com.fintrack.insight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * A {@link RestClient.Builder} for the service-to-service finance call (ADR 012).
 * Provided explicitly so the client's construction is independent of web
 * auto-configuration.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
