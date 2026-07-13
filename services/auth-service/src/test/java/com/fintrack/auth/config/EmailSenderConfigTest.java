package com.fintrack.auth.config;

import com.fintrack.auth.service.ResendEmailSender;
import com.fintrack.auth.service.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Transport selection: explicit MAIL_PROVIDER wins; auto = SMTP creds > Resend key > Mailpit. */
class EmailSenderConfigTest {

    private static final VerificationProperties PROPS = new VerificationProperties(
            4, Duration.ofMinutes(15), 5, Duration.ofSeconds(60), "no-reply@test");

    private final EmailSenderConfig config = new EmailSenderConfig();

    private Object choose(String provider, String smtpUsername, String resendKey) {
        return config.emailSender(new JavaMailSenderImpl(), PROPS,
                provider, smtpUsername, resendKey, "FinTrack <onboarding@resend.dev>");
    }

    @Test
    void autoPrefersSmtpCredentialsOverEverything() {
        assertThat(choose("auto", "someone@gmail.com", "some-resend-key"))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void autoUsesResendWhenOnlyTheKeyIsPresent() {
        assertThat(choose("auto", "", "some-resend-key")).isInstanceOf(ResendEmailSender.class);
    }

    @Test
    void autoFallsBackToTheLocalSink() {
        assertThat(choose("auto", "", "")).isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void explicitResendOverridesSmtpCredentials() {
        // the ./dev.sh resend case: .env has MAIL_USERNAME set, but the
        // explicit provider must win over the auto chain
        assertThat(choose("resend", "someone@gmail.com", "some-resend-key"))
                .isInstanceOf(ResendEmailSender.class);
    }

    @Test
    void explicitMailpitIgnoresAllCredentials() {
        assertThat(choose("mailpit", "someone@gmail.com", "some-resend-key"))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void explicitResendWithoutAKeyFailsFast() {
        assertThatIllegalStateException()
                .isThrownBy(() -> choose("resend", "", ""))
                .withMessageContaining("RESEND_API_KEY");
    }

    @Test
    void unknownProviderFailsFast() {
        assertThatIllegalStateException()
                .isThrownBy(() -> choose("carrier-pigeon", "", ""))
                .withMessageContaining("carrier-pigeon");
    }
}
