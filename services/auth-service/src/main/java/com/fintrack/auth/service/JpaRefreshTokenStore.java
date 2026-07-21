package com.fintrack.auth.service;

import com.fintrack.auth.config.JwtProperties;
import com.fintrack.auth.domain.RefreshToken;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Postgres-backed refresh-token store (ADR 011) — the default. This is the
 * original token logic (rotation chain + reuse detection) behind the
 * {@link RefreshTokenStore} seam, unchanged in behavior. Selected by
 * {@code fintrack.auth.refresh-token.store=jpa} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "fintrack.auth.refresh-token", name = "store",
        havingValue = "jpa", matchIfMissing = true)
public class JpaRefreshTokenStore implements RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(JpaRefreshTokenStore.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public JpaRefreshTokenStore(RefreshTokenRepository refreshTokenRepository,
                                UserRepository userRepository,
                                JwtProperties properties,
                                Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String issue(UUID userId) {
        String rawToken = newRawToken();
        User ref = userRepository.getReferenceById(userId);
        refreshTokenRepository.save(new RefreshToken(
                ref, TokenService.sha256Hex(rawToken), clock.instant().plus(properties.refreshTokenTtl())));
        return rawToken;
    }

    /**
     * noRollbackFor: the reuse-detection revocation cascade must survive the
     * 401 — a rollback would quietly undo the stolen-token response.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedRawToken) {
        Instant now = clock.instant();

        RefreshToken presented = refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(presentedRawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (presented.isRevoked()) {
            UUID userId = presented.getUser().getId();
            int killed = refreshTokenRepository.revokeAllActiveForUser(userId, now);
            log.warn("Refresh token reuse detected for user {} — revoked {} active session(s)", userId, killed);
            throw new InvalidRefreshTokenException();
        }
        if (presented.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        String successorRaw = newRawToken();
        RefreshToken successor = refreshTokenRepository.save(new RefreshToken(
                presented.getUser(), TokenService.sha256Hex(successorRaw),
                now.plus(properties.refreshTokenTtl())));
        presented.revokeAndReplace(successor.getId(), now);

        return new Rotation(successorRaw, presented.getUser().getId());
    }

    @Override
    @Transactional
    public void revoke(String presentedRawToken) {
        refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(presentedRawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Override
    @Transactional
    public int revokeAllForUser(UUID userId) {
        return refreshTokenRepository.revokeAllActiveForUser(userId, clock.instant());
    }

    @Override
    @Transactional
    public int purgeDeadTokensOlderThan(Instant cutoff) {
        return refreshTokenRepository.deleteDeadTokensOlderThan(cutoff);
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
