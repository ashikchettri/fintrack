package com.fintrack.insight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinTrack insight-service (ADR 012): AI spending insights over a household's
 * finance data. Reads finance-service through its API (forwarding the caller's
 * JWT), never its database.
 */
@SpringBootApplication
public class InsightServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InsightServiceApplication.class, args);
    }
}
