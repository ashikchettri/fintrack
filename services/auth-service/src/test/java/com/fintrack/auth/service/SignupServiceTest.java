package com.fintrack.auth.service;

import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.HouseholdRepository;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: repositories and encoder are mocked, so these verify the
 * orchestration logic only. Persistence behavior (constraints, transactions)
 * is covered by SignupIntegrationTest against real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private VerificationService verificationService;

    @InjectMocks
    private SignupService signupService;

    @Test
    void signupCreatesOwnerMembershipInNewHousehold() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct horse battery staple")).thenReturn("<argon2-hash>");

        SignupResult result = signupService.signup("jane@example.com", "correct horse battery staple");

        assertThat(result.user().getEmail()).isEqualTo("jane@example.com");
        assertThat(result.user().getPasswordHash()).isEqualTo("<argon2-hash>");
        assertThat(result.household().getName()).isEqualTo("jane's household");
        assertThat(result.member().getRole()).isEqualTo(HouseholdRole.OWNER);
        assertThat(result.member().getUser()).isSameAs(result.user());
        assertThat(result.member().getHousehold()).isSameAs(result.household());

        verify(userRepository).save(result.user());
        verify(householdRepository).save(result.household());
        verify(householdMemberRepository).save(result.member());
        // a verification code must be issued for every new account (ADR 004)
        verify(verificationService).issueFor(result.user());
    }

    @Test
    void emailIsNormalizedBeforeCheckAndStore() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("<argon2-hash>");

        SignupResult result = signupService.signup("  Jane@Example.COM ", "correct horse battery staple");

        // the existence check and the stored value must use the same normalization
        verify(userRepository).existsByEmail("jane@example.com");
        assertThat(result.user().getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void duplicateEmailIsRejectedWithoutWritingAnything() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatExceptionOfType(EmailAlreadyInUseException.class)
                .isThrownBy(() -> signupService.signup("jane@example.com", "correct horse battery staple"));

        verify(userRepository, never()).save(any());
        verify(householdRepository, never()).save(any());
        verify(householdMemberRepository, never()).save(any());
    }

    @Test
    void concurrentDuplicateInsertSurfacesAsEmailAlreadyInUse() {
        // two requests race past the existsByEmail check; the loser hits the
        // unique constraint at flush and must still get a 409, not a 500
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("<argon2-hash>");
        doThrow(new DataIntegrityViolationException("uq_users_email"))
                .when(userRepository).flush();

        assertThatExceptionOfType(EmailAlreadyInUseException.class)
                .isThrownBy(() -> signupService.signup("jane@example.com", "correct horse battery staple"));
    }

    @Test
    void rawPasswordIsNeverStoredOnTheUser() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct horse battery staple")).thenReturn("<argon2-hash>");

        SignupResult result = signupService.signup("jane@example.com", "correct horse battery staple");

        assertThat(result.user().getPasswordHash()).doesNotContain("correct horse battery staple");
    }
}