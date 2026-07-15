package com.fintrack.auth.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

import java.time.Duration;

/**
 * Resend transactional-email transport via the official SDK
 * (com.resend:resend-java). With the shared test sender
 * (onboarding@resend.dev) delivery is limited to the Resend account owner's
 * address until a domain is verified — flip RESEND_FROM after verification.
 */
public class ResendEmailSender implements EmailSender {

    private final Resend resend;
    private final String from;
    private final Duration codeTtl;

    public ResendEmailSender(String apiKey, String from, Duration codeTtl) {
        this(new Resend(apiKey), from, codeTtl);
    }

    // SDK client injectable for tests
    ResendEmailSender(Resend resend, String from, Duration codeTtl) {
        this.resend = resend;
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

    @Override
    public void sendEmailChangeCode(String toEmail, String code) {
        send(toEmail, EmailContent.emailChangeCode(code, codeTtl));
    }

    @Override
    public void sendHouseholdInvite(String toEmail, String inviterName, String householdName, String code) {
        send(toEmail, EmailContent.householdInvite(inviterName, householdName, code, EmailContent.INVITE_TTL));
    }

    private void send(String toEmail, EmailContent content) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(toEmail)
                .subject(content.subject())
                .text(content.text())
                .html(content.html())
                .build();
        try {
            resend.emails().send(options);
        } catch (ResendException e) {
            // surface the failure: the enclosing signup/reset transaction must
            // roll back rather than silently losing the email
            throw new IllegalStateException("Resend send failed", e);
        }
    }
}
