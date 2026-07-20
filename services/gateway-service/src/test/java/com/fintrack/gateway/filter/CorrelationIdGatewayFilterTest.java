package com.fintrack.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGatewayFilterTest {

    private final CorrelationIdGatewayFilter filter = new CorrelationIdGatewayFilter();

    /** Chain that records the (mutated) exchange it was handed. */
    private ServerWebExchange runWith(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            downstream.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        // the response header lives on the original exchange (mutate() shares it)
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER))
                .isEqualTo(downstream.get().getRequest().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER));
        return downstream.get();
    }

    @Test
    void honorsASafeInboundRequestId() {
        ServerWebExchange downstream = runWith(
                MockServerHttpRequest.get("/api/v1/accounts").header(CorrelationIdGatewayFilter.HEADER, "abc123def456"));

        assertThat(downstream.getRequest().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER))
                .isEqualTo("abc123def456");
    }

    @Test
    void mintsAnIdWhenNoneIsPresent() {
        ServerWebExchange downstream = runWith(MockServerHttpRequest.get("/api/v1/accounts"));

        assertThat(downstream.getRequest().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER))
                .matches("[A-Za-z0-9_-]{8,64}");
    }

    @Test
    void mintsANewIdWhenTheInboundOneLooksUnsafe() {
        // too short + contains an illegal char → rejected, minted instead
        ServerWebExchange downstream = runWith(
                MockServerHttpRequest.get("/api/v1/accounts").header(CorrelationIdGatewayFilter.HEADER, "bad id!"));

        String id = downstream.getRequest().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER);
        assertThat(id).isNotEqualTo("bad id!").matches("[A-Za-z0-9_-]{8,64}");
    }
}
