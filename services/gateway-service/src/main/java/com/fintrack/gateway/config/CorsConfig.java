package com.fintrack.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * The gateway is the only browser-facing origin, so it owns CORS (ADR 007).
 * {@code allowCredentials} is on because the refresh token is an httpOnly cookie
 * (ADR 003) — which is why the origin is explicit and never {@code *}.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsWebFilter corsWebFilter(
            @Value("${gateway.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {
        var config = new CorsConfiguration();
        config.addAllowedOrigin(allowedOrigin);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
