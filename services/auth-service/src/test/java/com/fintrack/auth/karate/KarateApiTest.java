package com.fintrack.auth.karate;

import com.fintrack.auth.TestcontainersConfiguration;
import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Boots the real app on a random port (backed by Testcontainers Postgres) and
 * runs the Karate features against it over actual HTTP — unlike MockMvc, this
 * exercises the servlet container, real serialization, and real headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class KarateApiTest {

    @LocalServerPort
    private int port;

    @Karate.Test
    Karate authApi() {
        // runs every feature under classpath:karate/auth
        return Karate.run("classpath:karate/auth")
                .systemProperty("app.baseUrl", "http://localhost:" + port);
    }
}