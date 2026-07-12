package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP transport: Mailpit locally (compose), or any authenticated SMTP (e.g.
 * Gmail App Password) via MAIL_* env vars. Selected by EmailSenderConfig.
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(toEmail);
        message.setSubject(content.subject());
        message.setText(content.text());
        mailSender.send(message);
    }
}
