package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.EmailVerificationCode;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.EmailVerificationCodeRepository;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final VerificationProperties PROPS = new VerificationProperties(
            6, Duration.ofMinutes(15), 5, Duration.ofSeconds(60), "no-reply@test");

    @Mock
    private EmailVerificationCodeRepository codeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailSender emailSender;

    private VerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new VerificationService(codeRepository, userRepository, emailSender,
                PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("jane@example.com", "<hash>");
    }

    private EmailVerificationCode issueAndCapture() {
        service.issueFor(user);
        ArgumentCaptor<String> rawCode = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq("jane@example.com"), rawCode.capture());
        ArgumentCaptor<EmailVerificationCode> saved =
                ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(codeRepository).save(saved.capture());
        // stash raw code on the test via the entity pair
        this.lastRawCode = rawCode.getValue();
        return saved.getValue();
    }

    private String lastRawCode;

    @Test
    void issuesAHashedSixDigitCodeAndEmailsTheRawOne() {
        EmailVerificationCode code = issueAndCapture();

        assertThat(lastRawCode).matches("\\d{6}");
        assertThat(code.getCodeHash())
                .isEqualTo(TokenService.sha256Hex(lastRawCode))
                .isNotEqualTo(lastRawCode);
        // replaces any prior code
        verify(codeRepository).deleteByUserId(user.getId());
    }

    @Test
    void correctCodeVerifiesTheUserAndConsumesTheCode() {
        EmailVerificationCode code = issueAndCapture();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(code));

        service.verify("  Jane@Example.COM ", lastRawCode);  // normalization too

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(code.isConsumed()).isTrue();
    }

    @Test
    void wrongCodeCountsAnAttemptAndFails() {
        EmailVerificationCode code = issueAndCapture();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(code));
        String wrong = "000000".equals(lastRawCode) ? "111111" : "000000";

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service.verify("jane@example.com", wrong));

        assertThat(code.getAttempts()).isEqualTo(1);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void codeDiesAfterMaxAttemptsEvenIfTheRightCodeArrivesLater() {
        EmailVerificationCode code = issueAndCapture();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(code));
        String wrong = "000000".equals(lastRawCode) ? "111111" : "000000";

        for (int i = 0; i < PROPS.maxAttempts(); i++) {
            assertThatExceptionOfType(InvalidVerificationCodeException.class)
                    .isThrownBy(() -> service.verify("jane@example.com", wrong));
        }

        // brute-force cap: the true code is now worthless
        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service.verify("jane@example.com", lastRawCode));
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void expiredCodeIsRejected() {
        EmailVerificationCode expired = new EmailVerificationCode(
                user, TokenService.sha256Hex("123456"), NOW.minusSeconds(1800), NOW.minusSeconds(900));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(expired));

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service.verify("jane@example.com", "123456"));
    }

    @Test
    void unknownEmailGetsTheSameGenericFailure() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service.verify("ghost@example.com", "123456"));
    }

    @Test
    void verifyingAnAlreadyVerifiedAccountIsIdempotent() {
        user.markEmailVerified(NOW);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        service.verify("jane@example.com", "whatever");  // must not throw

        verify(codeRepository, never()).findByUserId(any());
    }

    @Test
    void resendIsSilentForUnknownAndVerifiedEmails() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        service.resend("ghost@example.com");

        user.markEmailVerified(NOW);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        service.resend("jane@example.com");

        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    void resendInsideTheCooldownIsSilentlySkipped() {
        EmailVerificationCode recent = new EmailVerificationCode(
                user, "<hash>", NOW.minusSeconds(30), NOW.plusSeconds(870));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(recent));

        service.resend("jane@example.com");

        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    void resendAfterTheCooldownIssuesAFreshCode() {
        EmailVerificationCode old = new EmailVerificationCode(
                user, "<hash>", NOW.minusSeconds(120), NOW.plusSeconds(780));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(old));

        service.resend("jane@example.com");

        verify(emailSender).sendVerificationCode(eq("jane@example.com"), any());
        verify(codeRepository).deleteByUserId(user.getId());
    }
}
