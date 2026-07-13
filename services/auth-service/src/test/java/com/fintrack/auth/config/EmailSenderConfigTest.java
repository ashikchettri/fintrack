package com.fintrack.auth.config;

import com.fintrack.auth.service.ResendEmailSender;
import com.fintrack.auth.service.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The Attune-style provider chain: SMTP creds > Resend key > local SMTP sink. */
class EmailSenderConfigTest {

    private static final VerificationProperties PROPS = new VerificationProperties(
            4, Duration.ofMinutes(15), 5, Duration.ofSeconds(60), "no-reply@test");

    private final EmailSenderConfig config = new EmailSenderConfig();

    private Object choose(String smtpUsername, String resendKey) {
        return config.emailSender(new JavaMailSenderImpl(), PROPS,
                smtpUsername, resendKey, "FinTrack <onboarding@resend.dev>");
    }

    @Test
    void smtpCredentialsWinOverEverything() {
        assertThat(choose("attunemindbodymeal@gmail.com", "some-resend-key"))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void resendIsUsedWhenOnlyTheKeyIsPresent() {
        assertThat(choose("", "some-resend-key")).isInstanceOf(ResendEmailSender.class);
    }

    @Test
    void localSinkIsTheDefaultWhenNothingIsConfigured() {
        assertThat(choose("", "")).isInstanceOf(SmtpEmailSender.class);
    }
}
