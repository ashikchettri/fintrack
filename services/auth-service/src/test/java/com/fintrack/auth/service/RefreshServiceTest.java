package com.fintrack.auth.service;

import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.domain.RefreshToken;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    private static final String RAW = "raw-refresh-token-value";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private RefreshService refreshService;

    private User user;
    private HouseholdMember member;
    private RefreshToken presented;

    @BeforeEach
    void setUp() {
        user = new User("jane@example.com", "<hash>");
        member = new HouseholdMember(new Household("jane's household"), user, HouseholdRole.OWNER);
        presented = new RefreshToken(user, TokenService.sha256Hex(RAW),
                Instant.now().plus(Duration.ofDays(7)));
    }

    @Test
    void validTokenIsRotatedAndLinkedToSuccessor() {
        RefreshToken successor = new RefreshToken(user, "<successor-hash>",
                Instant.now().plus(Duration.ofDays(7)));
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));
        when(householdMemberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));
        when(tokenService.issueRefreshToken(user))
                .thenReturn(new TokenService.IssuedRefreshToken("new-raw-token", successor));
        when(tokenService.issueAccessToken(member)).thenReturn("<new-access-jwt>");

        LoginResult result = refreshService.refresh(RAW);

        assertThat(result.accessToken()).isEqualTo("<new-access-jwt>");
        assertThat(result.refreshToken()).isEqualTo("new-raw-token");
        // the presented token is dead and points at its successor
        assertThat(presented.isRevoked()).isTrue();
        assertThat(presented.getReplacedBy()).isEqualTo(successor.getId());
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> refreshService.refresh("no-such-token"));
    }

    @Test
    void expiredTokenIsRejectedWithoutRotation() {
        RefreshToken expired = new RefreshToken(user, TokenService.sha256Hex(RAW),
                Instant.now().minus(Duration.ofMinutes(1)));
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(expired));

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> refreshService.refresh(RAW));

        verify(tokenService, never()).issueRefreshToken(any());
    }

    @Test
    void reusedTokenKillsEveryActiveSessionForTheUser() {
        presented.revoke(Instant.now());  // already rotated once — this is a replay
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> refreshService.refresh(RAW));

        // the stolen-token response: nuke all live sessions, force re-login
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(user.getId()), any(Instant.class));
        verify(tokenService, never()).issueRefreshToken(any());
        verify(tokenService, never()).issueAccessToken(any());
    }

    @Test
    void logoutRevokesAnActiveToken() {
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));

        refreshService.logout(RAW);

        assertThat(presented.isRevoked()).isTrue();
        assertThat(presented.getReplacedBy()).isNull();  // logout, not rotation
    }

    @Test
    void logoutIsSilentForUnknownOrAlreadyRevokedTokens() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        refreshService.logout("never-existed");  // must not throw

        presented.revoke(Instant.now());
        Instant firstRevocation = presented.getRevokedAt();
        when(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(RAW)))
                .thenReturn(Optional.of(presented));
        refreshService.logout(RAW);  // must not throw, must not re-stamp

        assertThat(presented.getRevokedAt()).isEqualTo(firstRevocation);
    }
}
