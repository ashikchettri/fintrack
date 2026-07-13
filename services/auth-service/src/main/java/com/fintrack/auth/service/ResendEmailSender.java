package com.fintrack.auth.service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Resend transactional-email transport (https://resend.com), mirroring the
 * Attune project's integration. With the shared test sender
 * (onboarding@resend.dev) delivery is limited to the Resend account owner's
 * address until a domain is verified — flip RESEND_FROM after verification.
 */
public class ResendEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String from;
    private final Duration codeTtl;

    public ResendEmailSender(String apiKey, String from, Duration codeTtl) {
        this(apiKey, from, codeTtl, "https://api.resend.com");
    }

    // base URL injectable for tests
    ResendEmailSender(String apiKey, String from, Duration codeTtl, String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
        this.codeTtl = codeTtl;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        send(toEmail, EmailContent.verificationCode(code, codeTtl));
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, EmailContent.passwordResetCode(code, codeTtl));
    }

    private void send(String toEmail, EmailContent content) {
        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", List.of(toEmail),
                        "subject", content.subject(),
                        "text", content.text()))
                .retrieve()
                .toBodilessEntity();
    }
}
