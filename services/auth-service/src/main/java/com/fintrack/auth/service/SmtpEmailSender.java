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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(toEmail);
        message.setSubject("Your FinTrack verification code");
        message.setText("""
                Your FinTrack verification code is: %s

                It expires in %d minutes. If you didn't sign up, ignore this email.
                """.formatted(code, properties.codeTtl().toMinutes()));
        mailSender.send(message);
    }
}
