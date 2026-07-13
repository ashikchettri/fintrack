package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP transport: Mailpit locally (compose), or any authenticated SMTP (e.g.
 * Gmail App Password) via MAIL_* env vars. Selected by EmailSenderConfig.
 * Sends multipart text + HTML — the branded code block renders in Gmail just
 * like it does via Resend.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final VerificationProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, VerificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        send(toEmail, EmailContent.verificationCode(code, properties.codeTtl()));
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, EmailContent.passwordResetCode(code, properties.codeTtl()));
    }

    private void send(String toEmail, EmailContent content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.fromAddress());
            helper.setTo(toEmail);
            helper.setSubject(content.subject());
            // plain-text fallback + HTML alternative
            helper.setText(content.text(), content.html());
            mailSender.send(message);
        } catch (MessagingException e) {
            // surface the failure: the enclosing signup/reset transaction must
            // roll back rather than silently losing the email
            throw new IllegalStateException("SMTP send failed", e);
        }
    }
}
