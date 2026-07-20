package com.fintrack.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigTest {

    private final GatewayConfig config = new GatewayConfig();

    @Test
    void prefersTheFirstXForwardedForHopWhenBehindALoadBalancer() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/dashboard")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"));

        assertThat(GatewayConfig.clientIp(exchange)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToTheRemoteAddressWhenThereIsNoForwardedHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/dashboard")
                        .remoteAddress(new InetSocketAddress("198.51.100.4", 44321)));

        assertThat(GatewayConfig.clientIp(exchange)).isEqualTo("198.51.100.4");
    }

    @Test
    void returnsUnknownWhenNeitherHeaderNorRemoteAddressIsPresent() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/dashboard"));

        assertThat(GatewayConfig.clientIp(exchange)).isEqualTo("unknown");
    }

    @Test
    void keyResolverEmitsTheClientIp() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/dashboard")
                        .header("X-Forwarded-For", "203.0.113.7"));

        StepVerifier.create(config.clientIpKeyResolver().resolve(exchange))
                .expectNext("203.0.113.7")
                .verifyComplete();
    }
}
