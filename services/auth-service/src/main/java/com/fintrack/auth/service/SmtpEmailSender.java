package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final VerificationProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, VerificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        send(toEmail, "Your FinTrack verification code", """
                Your FinTrack verification code is: %s

                It expires in %d minutes. If you didn't sign up, ignore this email.
                """.formatted(code, properties.codeTtl().toMinutes()));
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, "Your FinTrack password reset code", """
                Your FinTrack password reset code is: %s

                It expires in %d minutes. If you didn't request a reset, you can
                ignore this email — your password is unchanged.
                """.formatted(code, properties.codeTtl().toMinutes()));
    }

    private void send(String toEmail, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
