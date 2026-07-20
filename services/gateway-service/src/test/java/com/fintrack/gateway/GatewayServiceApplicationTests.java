package com.fintrack.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the gateway boots with routing, CORS, and the Redis-backed rate
 * limiter all wired. Full route assertions (with stubbed downstreams) land with
 * the routing implementation slice — see ADR 007.
 */
@SpringBootTest
@Testcontainers
class GatewayServiceApplicationTests {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    void contextLoads() {
        // context startup exercises GatewayConfig (routes + rate limiter) and CorsConfig
    }
}
