package com.fintrack.auth.service;

import com.fintrack.auth.config.JwtProperties;
import com.fintrack.auth.domain.RefreshToken;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.RefreshTokenRepository;
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

/**
 * The token lifecycle — rotation chain, single-use, and reuse detection — as
 * enforced by the Postgres store (ADR 011).
 */
@ExtendWith(MockitoExtension.class)
class JpaRefreshTokenStoreTest {

    private static final String RAW = "raw-refresh-token-value";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    private JpaRefreshTokenStore store;
    private User user;
    private RefreshToken presented;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                null, "fintrack-auth", Duration.ofMinutes(15), Duration.ofDays(7));
        store = new JpaRefreshTokenStore(refreshTokenRepository, userRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User("jane@example.com", "<hash>");
        presented = new RefreshToken(user, TokenService.sha256Hex(RAW), NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void issuePersistsAHashedTokenAndReturnsTheRawValue() {
        when(userRepository.getReferenceById(user.getId())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = store.issue(user.getId());

        // 32 random bytes → 43 chars of padding-free base64url
        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        // stored value is the hash, never the raw token
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(TokenService.sha256Hex(raw)).isNotEqualTo(raw);
    }

    @Test
    void issuedTokensAreUnique() {
        when(userRepository.getReferenceById(user.getId())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(store.issue(user.getId())).isNotEqualTo(store.issue(user.getId()));
    }

    @Test
    void validTokenIsRotatedAndLinkedToSuccessor() {
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenStore.Rotation rotation = store.rotate(RAW);

        assertThat(rotation.rawToken()).isNotBlank().isNotEqualTo(RAW);
        assertThat(rotation.userId()).isEqualTo(user.getId());
        // the presented token is dead and points at its successor
        assertThat(presented.isRevoked()).isTrue();
        assertThat(presented.getReplacedBy()).isNotNull();
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate("no-such-token"));
    }

    @Test
    void expiredTokenIsRejectedWithoutRotation() {
        RefreshToken expired = new RefreshToken(user, TokenService.sha256Hex(RAW),
                NOW.minus(Duration.ofMinutes(1)));
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(expired));

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(RAW));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void reusedTokenKillsEveryActiveSessionForTheUser() {
        presented.revoke(Instant.now());  // already rotated once — this is a replay
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(RAW));

        // the stolen-token response: nuke all live sessions, force re-login
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(user.getId()), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logoutRevokesAnActiveToken() {
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));

        store.revoke(RAW);

        assertThat(presented.isRevoked()).isTrue();
        assertThat(presented.getReplacedBy()).isNull();  // logout, not rotation
    }

    @Test
    void logoutIsSilentForUnknownOrAlreadyRevokedTokens() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        store.revoke("never-existed");  // must not throw

        presented.revoke(Instant.now());
        Instant firstRevocation = presented.getRevokedAt();
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));
        store.revoke(RAW);  // must not throw, must not re-stamp

        assertThat(presented.getRevokedAt()).isEqualTo(firstRevocation);
    }

    @Test
    void revokeAllForUserDelegatesToTheBulkQuery() {
        when(refreshTokenRepository.revokeAllActiveForUser(eq(user.getId()), any(Instant.class)))
                .thenReturn(3);

        assertThat(store.revokeAllForUser(user.getId())).isEqualTo(3);
    }

    @Test
    void purgeDelegatesToTheCleanupQuery() {
        Instant cutoff = Instant.parse("2026-06-20T00:00:00Z");
        when(refreshTokenRepository.deleteDeadTokensOlderThan(cutoff)).thenReturn(7);

        assertThat(store.purgeDeadTokensOlderThan(cutoff)).isEqualTo(7);
    }
}
