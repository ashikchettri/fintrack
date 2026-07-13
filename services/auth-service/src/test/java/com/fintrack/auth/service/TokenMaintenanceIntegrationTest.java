package com.fintrack.auth.service;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.domain.RefreshToken;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Purge keeps live and recently-dead tokens, removes long-dead ones. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenMaintenanceIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private TokenMaintenanceService maintenance;  // real bean → @Transactional proxy applies

    private RefreshToken persisted(User user, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(user, "hash-" + Instant.now().getNano() + Math.random(), expiresAt);
        if (revokedAt != null) {
            token.revoke(revokedAt);
        }
        return refreshTokenRepository.save(token);
    }

    @Test
    void purgeRemovesOnlyTokensDeadBeyondTheRetentionWindow() {
        User user = userRepository.save(new User("purge-" + System.nanoTime() + "@example.com", "<hash>"));
        Instant now = clock.instant();
        Instant old = now.minus(Duration.ofDays(40));

        var live = persisted(user, now.plus(Duration.ofDays(7)), null);
        var recentlyExpired = persisted(user, now.minus(Duration.ofHours(1)), null);
        var longExpired = persisted(user, old, null);
        var longRevoked = persisted(user, now.plus(Duration.ofDays(7)), old);

        maintenance.purgeDeadTokens();

        assertThat(refreshTokenRepository.findById(live.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(recentlyExpired.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(longExpired.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(longRevoked.getId())).isEmpty();
    }
}
