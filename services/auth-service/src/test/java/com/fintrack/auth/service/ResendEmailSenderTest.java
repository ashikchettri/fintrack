package com.fintrack.auth.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Asserts the exact request handed to the official Resend SDK. */
@ExtendWith(MockitoExtension.class)
class ResendEmailSenderTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        when(resend.emails()).thenReturn(emails);
        sender = new ResendEmailSender(resend, "FinTrack <onboarding@resend.dev>",
                Duration.ofMinutes(15));
    }

    private CreateEmailOptions captureSent() throws ResendException {
        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendsTheVerificationEmailWithTextAndHtml() throws ResendException {
        sender.sendVerificationCode("jane@example.com", "1234");

        CreateEmailOptions sent = captureSent();
        assertThat(sent.getFrom()).isEqualTo("FinTrack <onboarding@resend.dev>");
        assertThat(sent.getTo()).containsExactly("jane@example.com");
        assertThat(sent.getSubject()).isEqualTo("Your FinTrack verification code");
        assertThat(sent.getText()).contains("1234").contains("15 minutes");
        assertThat(sent.getHtml()).contains("1234").contains("Verify your email");
    }

    @Test
    void sendsTheResetEmailWithTheResetCopy() throws ResendException {
        sender.sendPasswordResetCode("jane@example.com", "123456");

        CreateEmailOptions sent = captureSent();
        assertThat(sent.getSubject()).isEqualTo("Your FinTrack password reset code");
        assertThat(sent.getText()).contains("123456").contains("your password is unchanged");
        assertThat(sent.getHtml()).contains("123456").contains("Reset your password");
    }

    @Test
    void sdkFailuresSurfaceAsExceptions() throws ResendException {
        // a failed send must fail the signup/reset transaction, not vanish silently
        when(emails.send(any())).thenThrow(new ResendException("boom"));

        assertThatIllegalStateException()
                .isThrownBy(() -> sender.sendVerificationCode("jane@example.com", "1234"))
                .withMessageContaining("Resend send failed");
    }
}
