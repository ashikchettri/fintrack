package com.fintrack.auth.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Drives the real HTTP path against a stub Resend API (JDK HttpServer). */
class ResendEmailSenderTest {

    private record CapturedRequest(String path, String authorization, String body) {
    }

    private static HttpServer server;
    private static final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private static volatile int responseStatus = 200;

    private ResendEmailSender sender;

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            captured.set(new CapturedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = "{\"id\":\"stub\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @BeforeEach
    void setUp() {
        responseStatus = 200;
        captured.set(null);
        sender = new ResendEmailSender("test-api-key", "FinTrack <onboarding@resend.dev>",
                Duration.ofMinutes(15),
                "http://localhost:" + server.getAddress().getPort());
    }

    @Test
    void postsTheVerificationEmailWithBearerAuth() {
        sender.sendVerificationCode("jane@example.com", "1234");

        CapturedRequest request = captured.get();
        assertThat(request.path()).isEqualTo("/emails");
        assertThat(request.authorization()).isEqualTo("Bearer test-api-key");
        assertThat(request.body())
                .contains("\"jane@example.com\"")
                .contains("Your FinTrack verification code")
                .contains("1234")
                .contains("onboarding@resend.dev");
    }

    @Test
    void postsTheResetEmailWithTheResetCopy() {
        sender.sendPasswordResetCode("jane@example.com", "123456");

        assertThat(captured.get().body())
                .contains("Your FinTrack password reset code")
                .contains("123456")
                .contains("your password is unchanged");
    }

    @Test
    void apiErrorsSurfaceAsExceptions() {
        responseStatus = 422;

        // a failed send must fail the signup transaction, not vanish silently
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> sender.sendVerificationCode("jane@example.com", "1234"));
    }
}
