package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    private static final VerificationProperties PROPS = new VerificationProperties(
            6, Duration.ofMinutes(15), 5, Duration.ofSeconds(60), "no-reply@fintrack.test");

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        sender = new SmtpEmailSender(mailSender, PROPS);
    }

    private MimeMessage captureSent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    /** Walks the multipart tree collecting all body text (plain + html parts). */
    private static String allBodies(MimeMessage message) throws Exception {
        StringBuilder out = new StringBuilder();
        collect(message.getContent(), out);
        return out.toString();
    }

    private static void collect(Object content, StringBuilder out) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i).getContent(), out);
            }
        } else {
            out.append(content).append('\n');
        }
    }

    @Test
    void verificationEmailCarriesTextAndBrandedHtml() throws Exception {
        sender.sendVerificationCode("jane@example.com", "123456");

        MimeMessage sent = captureSent();
        assertThat(sent.getSubject()).isEqualTo("Your FinTrack verification code");
        assertThat(sent.getAllRecipients()[0]).hasToString("jane@example.com");
        // both alternatives present: plain text and the styled HTML block
        assertThat(allBodies(sent))
                .contains("Your FinTrack verification code is: 123456")
                .contains("Verify your email")
                .contains("letter-spacing");
    }

    @Test
    void resetEmailCarriesTheResetCopy() throws Exception {
        sender.sendPasswordResetCode("jane@example.com", "654321");

        String bodies = allBodies(captureSent());
        assertThat(bodies).contains("654321")
                .contains("your password is unchanged")
                .contains("Reset your password");
    }
}
