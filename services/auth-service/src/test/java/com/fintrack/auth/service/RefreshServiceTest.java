package com.fintrack.auth.service;

import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefreshService orchestrates over the {@link RefreshTokenStore}: the token
 * lifecycle (rotation, reuse detection) is the store's job and is tested in
 * {@link JpaRefreshTokenStoreTest}. Here we only cover the mapping of a rotation
 * to a fresh access token, and logout delegation.
 */
@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private HouseholdMemberRepository householdMemberRepository;
    @Mock
    private TokenService tokenService;

    @InjectMocks
    private RefreshService refreshService;

    private User user;
    private HouseholdMember member;

    @BeforeEach
    void setUp() {
        user = new User("jane@example.com", "<hash>");
        member = new HouseholdMember(new Household("jane's household"), user, HouseholdRole.OWNER);
    }

    @Test
    void rotatesTheTokenAndMintsAFreshAccessToken() {
        when(refreshTokenStore.rotate("raw"))
                .thenReturn(new RefreshTokenStore.Rotation("new-raw", user.getId()));
        when(householdMemberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));
        when(tokenService.issueAccessToken(member)).thenReturn("<new-access-jwt>");

        LoginResult result = refreshService.refresh("raw");

        assertThat(result.accessToken()).isEqualTo("<new-access-jwt>");
        assertThat(result.refreshToken()).isEqualTo("new-raw");
    }

    @Test
    void propagatesTheStoresRejectionWithoutMintingAToken() {
        when(refreshTokenStore.rotate(any())).thenThrow(new InvalidRefreshTokenException());

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> refreshService.refresh("bad"));
        verify(tokenService, never()).issueAccessToken(any());
    }

    @Test
    void logoutDelegatesToTheStore() {
        refreshService.logout("raw");
        verify(refreshTokenStore).revoke("raw");
    }
}
