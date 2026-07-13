package com.fintrack.auth.config;

import com.fintrack.auth.service.EmailSender;
import com.fintrack.auth.service.ResendEmailSender;
import com.fintrack.auth.service.SmtpEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Provider chain mirroring the Attune project (ADR 004 transport):
 *   1. authenticated SMTP if MAIL_USERNAME/MAIL_PASSWORD are set
 *      (e.g. Gmail App Password — reaches any recipient, no domain needed);
 *   2. Resend if RESEND_API_KEY is set (test sender delivers only to the
 *      account owner until a domain is verified);
 *   3. otherwise plain SMTP to the local Mailpit sink (dev default).
 */
@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    EmailSender emailSender(JavaMailSender mailSender,
                            VerificationProperties properties,
                            @Value("${spring.mail.username:}") String smtpUsername,
                            @Value("${fintrack.auth.resend.api-key:}") String resendApiKey,
                            @Value("${fintrack.auth.resend.from:FinTrack <onboarding@resend.dev>}") String resendFrom) {
        if (!smtpUsername.isBlank()) {
            log.info("Email transport: authenticated SMTP as {}", smtpUsername);
            return new SmtpEmailSender(mailSender, properties);
        }
        if (!resendApiKey.isBlank()) {
            log.info("Email transport: Resend (from {})", resendFrom);
            return new ResendEmailSender(resendApiKey, resendFrom, properties.codeTtl());
        }
        log.info("Email transport: unauthenticated SMTP (local Mailpit sink)");
        return new SmtpEmailSender(mailSender, properties);
    }
}
