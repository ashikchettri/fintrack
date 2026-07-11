package com.fintrack.auth.service;

import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.RefreshToken;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final TokenService tokenService;

    public RefreshService(RefreshTokenRepository refreshTokenRepository,
                          HouseholdMemberRepository householdMemberRepository,
                          TokenService tokenService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.tokenService = tokenService;
    }

    /**
     * Rotation: each refresh token is single-use. The presented token is
     * revoked and linked to its successor.
     *
     * Reuse detection: a *revoked* token showing up again means two parties
     * hold the same token — the legitimate client already rotated it, so this
     * presenter (or the one that rotated) is a thief. We can't tell which, so
     * every live session for the user is revoked; both parties must
     * re-authenticate with the password.
     *
     * noRollbackFor: the revocation cascade is the point — a rollback on the
     * 401 would quietly undo the stolen-token response (a test proved it did).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResult refresh(String rawRefreshToken) {
        Instant now = Instant.now();

        RefreshToken presented = refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (presented.isRevoked()) {
            UUID userId = presented.getUser().getId();
            int killed = refreshTokenRepository.revokeAllActiveForUser(userId, now);
            log.warn("Refresh token reuse detected for user {} — revoked {} active session(s)",
                    userId, killed);
            throw new InvalidRefreshTokenException();
        }
        if (presented.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        HouseholdMember member = householdMemberRepository
                .findByUserId(presented.getUser().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "user %s has no household membership".formatted(presented.getUser().getId())));

        TokenService.IssuedRefreshToken successor =
                tokenService.issueRefreshToken(presented.getUser());
        presented.revokeAndReplace(successor.token().getId(), now);

        return new LoginResult(tokenService.issueAccessToken(member), successor.rawToken());
    }

    /**
     * Logout revokes the presented refresh token. Idempotent and silent:
     * always succeeds from the client's perspective, revealing nothing about
     * whether the token existed. The access token stays valid until its
     * 15-minute expiry — the accepted trade-off of stateless JWTs.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(Instant.now()));
    }
}
