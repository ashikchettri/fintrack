package com.fintrack.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class AuthServiceApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry registry) {
        // mirror the schema-per-service setup used in docker-compose
        registry.add("spring.flyway.create-schemas", () -> "true");
    }

    @Test
    void contextLoads() {
        // Boots the full Spring context against a real throwaway Postgres.
        // Fails if wiring, Flyway migrations, or JPA mappings are broken.
    }
}
