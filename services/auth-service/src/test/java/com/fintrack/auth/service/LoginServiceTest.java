package com.fintrack.auth.service;

import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EQUALIZER_HASH = "<equalizer-hash>";

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    private LoginService loginService;

    private User user;
    private HouseholdMember member;

    @BeforeEach
    void setUp() {
        // the constructor pre-computes the timing-equalizer hash
        when(passwordEncoder.encode(anyString())).thenReturn(EQUALIZER_HASH);
        loginService = new LoginService(
                userRepository, householdMemberRepository, passwordEncoder, tokenService,
                loginAttemptService);

        user = new User("jane@example.com", "<stored-hash>");
        user.markEmailVerified(java.time.Instant.now());  // login requires it (ADR 004)
        member = new HouseholdMember(new Household("jane's household"), user, HouseholdRole.OWNER);
    }

    private TokenService.IssuedRefreshToken issuedRefreshToken(String raw) {
        return new TokenService.IssuedRefreshToken(
                raw, new com.fintrack.auth.domain.RefreshToken(user, "<hash>", java.time.Instant.now()));
    }

    @Test
    void successfulLoginIssuesBothTokens() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct horse battery staple", "<stored-hash>")).thenReturn(true);
        when(householdMemberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));
        when(tokenService.issueAccessToken(member)).thenReturn("<access-jwt>");
        when(tokenService.issueRefreshToken(user)).thenReturn(issuedRefreshToken("<refresh-token>"));

        LoginResult result = loginService.login("jane@example.com", "correct horse battery staple");

        assertThat(result.accessToken()).isEqualTo("<access-jwt>");
        assertThat(result.refreshToken()).isEqualTo("<refresh-token>");
    }

    @Test
    void emailIsNormalizedBeforeLookup() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(householdMemberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));
        when(tokenService.issueRefreshToken(user)).thenReturn(issuedRefreshToken("<refresh-token>"));

        loginService.login("  Jane@Example.COM ", "correct horse battery staple");

        verify(userRepository).findByEmail("jane@example.com");
    }

    @Test
    void unknownEmailFailsButStillBurnsAnArgon2Check() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> loginService.login("ghost@example.com", "whatever-password"));

        // timing defense: the equalizer hash must be verified against, so the
        // unknown-email path costs the same as a real password check
        verify(passwordEncoder).matches("whatever-password", EQUALIZER_HASH);
        verifyNoInteractions(tokenService);
    }

    @Test
    void wrongPasswordFailsWithTheSameException() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password-here", "<stored-hash>")).thenReturn(false);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> loginService.login("jane@example.com", "wrong-password-here"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void throttledEmailIsRejectedBeforeAnyCredentialWork() {
        doThrow(new TooManyLoginAttemptsException())
                .when(loginAttemptService).checkNotThrottled("jane@example.com");

        assertThatExceptionOfType(TooManyLoginAttemptsException.class)
                .isThrownBy(() -> loginService.login("jane@example.com", "any-password-at-all"));

        // no lookup, no Argon2 run — the throttle is the cheap outer gate
        verifyNoInteractions(userRepository, tokenService);
    }

    @Test
    void failuresAreRecordedAndSuccessClearsThem() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password-here", "<stored-hash>")).thenReturn(false);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> loginService.login("jane@example.com", "wrong-password-here"));
        verify(loginAttemptService).recordFailure("jane@example.com");

        when(passwordEncoder.matches("correct horse battery staple", "<stored-hash>")).thenReturn(true);
        when(householdMemberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));
        when(tokenService.issueRefreshToken(user)).thenReturn(issuedRefreshToken("<refresh-token>"));

        loginService.login("jane@example.com", "correct horse battery staple");
        verify(loginAttemptService).recordSuccess("jane@example.com");
    }

    @Test
    void missingMembershipIsDataCorruptionNotA401() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(householdMemberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatIllegalStateException()
                .isThrownBy(() -> loginService.login("jane@example.com", "correct horse battery staple"))
                .withMessageContaining("household membership");
    }
}
