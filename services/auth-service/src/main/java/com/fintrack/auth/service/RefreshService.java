package com.fintrack.auth.service;

import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the refresh + logout flows over the {@link RefreshTokenStore}
 * (ADR 011). The token lifecycle (rotation, single-use, reuse detection) lives
 * in the store; this service maps a successful rotation to a fresh access token.
 */
@Service
public class RefreshService {

    private final RefreshTokenStore refreshTokenStore;
    private final HouseholdMemberRepository householdMemberRepository;
    private final TokenService tokenService;

    public RefreshService(RefreshTokenStore refreshTokenStore,
                          HouseholdMemberRepository householdMemberRepository,
                          TokenService tokenService) {
        this.refreshTokenStore = refreshTokenStore;
        this.householdMemberRepository = householdMemberRepository;
        this.tokenService = tokenService;
    }

    /**
     * Rotate the refresh token and mint a new access token. The store enforces
     * single-use + reuse detection (a replayed rotated token revokes every
     * session and throws {@link InvalidRefreshTokenException}).
     */
    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenStore.Rotation rotation = refreshTokenStore.rotate(rawRefreshToken);

        HouseholdMember member = householdMemberRepository
                .findByUserId(rotation.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "user %s has no household membership".formatted(rotation.userId())));

        return new LoginResult(tokenService.issueAccessToken(member), rotation.rawToken());
    }

    /**
     * Logout revokes the presented refresh token. Idempotent and silent: it
     * reveals nothing about whether the token existed. The access token stays
     * valid until its 15-minute expiry — the accepted trade-off of stateless JWTs.
     */
    public void logout(String rawRefreshToken) {
        refreshTokenStore.revoke(rawRefreshToken);
    }
}
