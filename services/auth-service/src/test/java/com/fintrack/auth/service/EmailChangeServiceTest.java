package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.EmailChangeRequest;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.EmailChangeRequestRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
    private static final VerificationProperties PROPS = new VerificationProperties(
            6, Duration.ofMinutes(15), 5, Duration.ofSeconds(60), "no-reply@test");

    @Mock
    private EmailChangeRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSender emailSender;

    private EmailChangeService service;
    private User user;
    private String rawCode;

    @BeforeEach
    void setUp() {
        service = new EmailChangeService(requestRepository, userRepository, passwordEncoder,
                emailSender, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("old@example.com", "<hash>");
    }

    private EmailChangeRequest requestAndCapture() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "<hash>")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        service.requestChange(user.getId(), "  New@Example.COM ", "current-pw");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendEmailChangeCode(eq("new@example.com"), code.capture());
        rawCode = code.getValue();
        ArgumentCaptor<EmailChangeRequest> saved = ArgumentCaptor.forClass(EmailChangeRequest.class);
        verify(requestRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void requestEmailsACodeToTheNormalizedNewAddressWithoutSwapping() {
        EmailChangeRequest request = requestAndCapture();

        assertThat(rawCode).matches("\\d{6}");
        assertThat(request.getNewEmail()).isEqualTo("new@example.com");
        assertThat(request.getCodeHash()).isEqualTo(TokenService.sha256Hex(rawCode));
        // old address is untouched until confirmation
        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "<hash>")).thenReturn(false);

        assertThatExceptionOfType(IncorrectCurrentPasswordException.class)
                .isThrownBy(() -> service.requestChange(user.getId(), "new@example.com", "wrong"));
        verify(emailSender, never()).sendEmailChangeCode(any(), any());
    }

    @Test
    void newAddressAlreadyInUseIsRejected() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "<hash>")).thenReturn(true);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatExceptionOfType(EmailAlreadyInUseException.class)
                .isThrownBy(() -> service.requestChange(user.getId(), "taken@example.com", "current-pw"));
    }

    @Test
    void confirmSwapsTheEmailOnTheCorrectCode() {
        EmailChangeRequest request = requestAndCapture();
        when(requestRepository.findByUserId(user.getId())).thenReturn(Optional.of(request));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        service.confirmChange(user.getId(), rawCode);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void confirmWithWrongCodeCountsAnAttemptAndKeepsOldEmail() {
        EmailChangeRequest request = requestAndCapture();
        when(requestRepository.findByUserId(user.getId())).thenReturn(Optional.of(request));
        String wrong = rawCode.equals("000000") ? "111111" : "000000";

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service.confirmChange(user.getId(), wrong));

        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void confirmWithNoPendingRequestFails() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(requestRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service.confirmChange(user.getId(), "123456"));
    }
}
