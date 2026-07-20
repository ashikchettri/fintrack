package com.fintrack.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinTrack API gateway (ADR 007): the single public entry point. Routes to
 * auth-service and finance-service, owns CORS, and rate-limits at the edge.
 * It never authenticates — the services keep validating JWTs themselves.
 */
@SpringBootApplication
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
