package com.fintrack.auth.service;

import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
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
class ChangePasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ChangePasswordService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ChangePasswordService(userRepository, refreshTokenRepository,
                passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("jane@example.com", "<old-hash>");
    }

    @Test
    void changesPasswordAndRevokesOtherSessions() {
        UUID id = user.getId();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-secret-here", "<old-hash>")).thenReturn(true);
        when(passwordEncoder.encode("brand new secret here")).thenReturn("<new-hash>");

        service.changePassword(id, "current-secret-here", "brand new secret here");

        assertThat(user.getPasswordHash()).isEqualTo("<new-hash>");
        // a password change evicts every active session (defense in depth)
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(id), any(Instant.class));
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        UUID id = user.getId();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-current", "<old-hash>")).thenReturn(false);

        assertThatExceptionOfType(IncorrectCurrentPasswordException.class)
                .isThrownBy(() -> service.changePassword(id, "wrong-current", "brand new secret here"));

        assertThat(user.getPasswordHash()).isEqualTo("<old-hash>");
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void unknownAuthenticatedSubjectIsDataCorruption() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.changePassword(id, "x", "brand new secret here"));
    }
}
