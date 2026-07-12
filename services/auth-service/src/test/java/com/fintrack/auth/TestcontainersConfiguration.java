package com.fintrack.auth;

import com.fintrack.auth.service.EmailSender;
import com.fintrack.auth.testsupport.RecordingEmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared throwaway Postgres for integration tests. @ServiceConnection wires
 * the datasource automatically; Flyway creates + migrates the `auth` schema,
 * so tests run against the exact schema production will have.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    // no SMTP in tests: codes are captured, and Karate reads them via Java interop
    @Bean
    @Primary
    EmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }
}
