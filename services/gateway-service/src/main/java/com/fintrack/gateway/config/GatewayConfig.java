package com.fintrack.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Routing + rate limiting (ADR 007). Routes are the fluent {@code RouteLocator}
 * builder — compile-checked and stable across Spring Cloud release trains, where
 * the equivalent YAML property paths have churned.
 *
 * <p>The finance route is declared first so its specific prefixes win over the
 * {@code /api/**} auth catch-all. The singular {@code /household/**} (finance)
 * never matches the plural {@code /households/**} (auth), so household roster
 * calls fall through to auth exactly as the dev proxy does today.
 */
@Configuration
public class GatewayConfig {

    @Bean
    RouteLocator routes(
            RouteLocatorBuilder builder,
            RedisRateLimiter rateLimiter,
            KeyResolver clientIpKeyResolver,
            @Value("${gateway.auth-uri:http://localhost:8081}") String authUri,
            @Value("${gateway.finance-uri:http://localhost:8082}") String financeUri) {
        return builder.routes()
                .route("finance-service", r -> r
                        .path(
                                "/api/v1/accounts/**",
                                "/api/v1/transactions/**",
                                "/api/v1/imports/**",
                                "/api/v1/dashboard/**",
                                "/api/v1/household/**")   // singular → finance
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(rateLimiter)
                                .setKeyResolver(clientIpKeyResolver)))
                        .uri(financeUri))
                .route("auth-service", r -> r
                        .path("/api/**")                  // everything else, incl. /households/**
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(rateLimiter)
                                .setKeyResolver(clientIpKeyResolver)))
                        .uri(authUri))
                .build();
    }

    /**
     * Token bucket: {@code replenishRate} tokens/sec steady state, up to
     * {@code burstCapacity} in a spike. Backed by Redis so the limit is shared
     * across gateway replicas.
     */
    @Bean
    RedisRateLimiter rateLimiter(
            @Value("${gateway.rate-limit.replenish-rate:20}") int replenishRate,
            @Value("${gateway.rate-limit.burst-capacity:40}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    /**
     * v1 keys the rate limit on the client IP (honours {@code X-Forwarded-For}
     * when a load balancer sits in front). Per-user keying by JWT subject is the
     * next slice — see ADR 007.
     */
    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange));
    }

    static String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}
