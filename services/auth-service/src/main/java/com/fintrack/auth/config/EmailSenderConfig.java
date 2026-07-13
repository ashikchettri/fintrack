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

import java.util.Locale;

/**
 * Email transport selection (ADR 004).
 *
 * Explicit override first: MAIL_PROVIDER = smtp | resend | mailpit
 * (e.g. `./dev.sh resend` for real delivery from local dev).
 *
 * Otherwise "auto", the Attune-style chain:
 *   1. authenticated SMTP if MAIL_USERNAME is set (e.g. Gmail App Password);
 *   2. Resend if RESEND_API_KEY is set (test sender delivers only to the
 *      account owner until a domain is verified);
 *   3. plain SMTP to the local Mailpit sink (dev default).
 */
@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    EmailSender emailSender(JavaMailSender mailSender,
                            VerificationProperties properties,
                            @Value("${fintrack.auth.mail-provider:auto}") String provider,
                            @Value("${spring.mail.username:}") String smtpUsername,
                            @Value("${fintrack.auth.resend.api-key:}") String resendApiKey,
                            @Value("${fintrack.auth.resend.from:FinTrack <onboarding@resend.dev>}") String resendFrom) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "resend" -> {
                if (resendApiKey.isBlank()) {
                    // fail fast: an explicit choice with missing credentials is a
                    // misconfiguration, not something to silently fall back from
                    throw new IllegalStateException(
                            "MAIL_PROVIDER=resend requires RESEND_API_KEY to be set");
                }
                yield resend(resendApiKey, resendFrom, properties);
            }
            case "smtp" -> smtp(mailSender, properties, "explicit SMTP via spring.mail settings");
            // hard-wired local sink: mailpit mode must stay local even if Gmail
            // credentials happen to be present in the environment
            case "mailpit" -> smtp(localSink(), properties, "local Mailpit sink");
            case "auto" -> {
                if (!smtpUsername.isBlank()) {
                    yield smtp(mailSender, properties, "authenticated SMTP as " + smtpUsername);
                }
                if (!resendApiKey.isBlank()) {
                    yield resend(resendApiKey, resendFrom, properties);
                }
                yield smtp(mailSender, properties, "local Mailpit sink");
            }
            default -> throw new IllegalStateException(
                    "Unknown MAIL_PROVIDER '" + provider + "' — use smtp, resend, mailpit, or auto");
        };
    }

    private EmailSender resend(String apiKey, String from, VerificationProperties properties) {
        log.info("Email transport: Resend (from {})", from);
        return new ResendEmailSender(apiKey, from, properties.codeTtl());
    }

    private EmailSender smtp(JavaMailSender mailSender, VerificationProperties properties, String label) {
        log.info("Email transport: SMTP ({})", label);
        return new SmtpEmailSender(mailSender, properties);
    }

    private JavaMailSender localSink() {
        var sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(1025);   // Mailpit's fixed compose port
        return sender;
    }
}
