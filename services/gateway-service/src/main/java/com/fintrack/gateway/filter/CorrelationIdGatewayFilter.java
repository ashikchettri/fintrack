package com.fintrack.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mints the request correlation id at the edge (ADR 010) — the gateway is the
 * single entry point, so it's the authoritative source. Honors a safe inbound
 * X-Request-Id, else generates one; forwards it downstream (auth/finance honor
 * it and log under it) and echoes it to the client, so even a gateway-level
 * error (rate limit, no route) carries a traceable id.
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders().getFirst(HEADER);
        String traceId = (inbound != null && SAFE_ID.matcher(inbound).matches())
                ? inbound
                : UUID.randomUUID().toString();

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, traceId)))
                .build();
        mutated.getResponse().getHeaders().set(HEADER, traceId);
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
