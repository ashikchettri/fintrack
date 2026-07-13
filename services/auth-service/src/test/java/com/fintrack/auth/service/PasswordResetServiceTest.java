package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.PasswordResetCode;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.PasswordResetCodeRepository;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final VerificationProperties PROPS = new VerificationProperties(
            6, Duration.ofMinutes(15), 5, Duration.ofSeconds(60), "no-reply@test");

    @Mock
    private PasswordResetCodeRepository codeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private EmailSender emailSender;

    private PasswordResetService service;
    private User user;
    private String rawCode;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(codeRepository, userRepository,
                refreshTokenRepository, passwordEncoder, loginAttemptService,
                emailSender, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("jane@example.com", "<old-hash>");
    }

    private PasswordResetCode requestAndCapture() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        service.requestReset("jane@example.com");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordResetCode(eq("jane@example.com"), code.capture());
        rawCode = code.getValue();
        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(codeRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void issuesASixDigitHashedCode() {
        PasswordResetCode code = requestAndCapture();

        assertThat(rawCode).matches("\\d{6}");
        assertThat(code.getCodeHash()).isEqualTo(TokenService.sha256Hex(rawCode));
    }

    @Test
    void unknownEmailIsSilent() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verify(emailSender, never()).sendPasswordResetCode(any(), any());
    }

    @Test
    void requestInsideCooldownIsSilent() {
        PasswordResetCode recent = new PasswordResetCode(
                user, "<hash>", NOW.minusSeconds(30), NOW.plusSeconds(870));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(recent));

        service.requestReset("jane@example.com");

        verify(emailSender, never()).sendPasswordResetCode(any(), any());
    }

    @Test
    void successfulResetRotatesPasswordRevokesSessionsAndVerifies() {
        PasswordResetCode code = requestAndCapture();
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(code));
        when(passwordEncoder.encode("brand new password here")).thenReturn("<new-hash>");

        service.reset("  Jane@Example.COM ", rawCode, "brand new password here");

        assertThat(user.getPasswordHash()).isEqualTo("<new-hash>");
        assertThat(code.isDead(NOW, PROPS.maxAttempts())).isTrue();
        // stolen sessions die with the old password (ADR 005)
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(user.getId()), any(Instant.class));
        // emailed-code entry doubles as mailbox proof
        assertThat(user.isEmailVerified()).isTrue();
        verify(loginAttemptService).recordSuccess("jane@example.com");
    }

    @Test
    void wrongCodeCountsAnAttemptAndChangesNothing() {
        PasswordResetCode code = requestAndCapture();
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(code));
        String wrong = rawCode.equals("000000") ? "111111" : "000000";

        assertThatExceptionOfType(InvalidResetCodeException.class)
                .isThrownBy(() -> service.reset("jane@example.com", wrong, "brand new password here"));

        assertThat(code.getAttempts()).isEqualTo(1);
        assertThat(user.getPasswordHash()).isEqualTo("<old-hash>");
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void attemptCapKillsTheCodeForGood() {
        PasswordResetCode code = requestAndCapture();
        when(codeRepository.findByUserId(user.getId())).thenReturn(Optional.of(code));
        String wrong = rawCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < PROPS.maxAttempts(); i++) {
            assertThatExceptionOfType(InvalidResetCodeException.class)
                    .isThrownBy(() -> service.reset("jane@example.com", wrong, "brand new password here"));
        }
        assertThatExceptionOfType(InvalidResetCodeException.class)
                .isThrownBy(() -> service.reset("jane@example.com", rawCode, "brand new password here"));
        assertThat(user.getPasswordHash()).isEqualTo("<old-hash>");
    }

    @Test
    void unknownEmailOnResetGetsTheSameGenericFailure() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidResetCodeException.class)
                .isThrownBy(() -> service.reset("ghost@example.com", "123456", "brand new password here"));
    }
}
