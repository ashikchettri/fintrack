package com.fintrack.auth.service;

import com.fintrack.auth.config.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The Redis refresh-token store over a real Redis (ADR 011): rotation chain,
 * single-use, reuse detection, logout, and bulk revocation — the same behaviors
 * the Postgres store enforces.
 */
@Testcontainers
class RedisRefreshTokenStoreTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");
    private static final JwtProperties PROPS =
            new JwtProperties(null, "fintrack-auth", Duration.ofMinutes(15), Duration.ofDays(7));

    private LettuceConnectionFactory connectionFactory;
    private RedisRefreshTokenStore store;
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try (RedisConnection conn = connectionFactory.getConnection()) {
            conn.serverCommands().flushDb();   // isolate each test
        }
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        store = storeAt(template, NOW);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    private RedisRefreshTokenStore storeAt(StringRedisTemplate template, Instant instant) {
        return new RedisRefreshTokenStore(template, PROPS, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuesRotatesAndKeepsTheChainUsable() {
        String raw = store.issue(user);

        RefreshTokenStore.Rotation first = store.rotate(raw);
        assertThat(first.userId()).isEqualTo(user);
        assertThat(first.rawToken()).isNotBlank().isNotEqualTo(raw);

        // the successor is itself rotatable — the lineage continues
        RefreshTokenStore.Rotation second = store.rotate(first.rawToken());
        assertThat(second.rawToken()).isNotBlank().isNotEqualTo(first.rawToken());
    }

    @Test
    void replayingAConsumedTokenIsReuseAndKillsEverySession() {
        String raw = store.issue(user);
        RefreshTokenStore.Rotation rotated = store.rotate(raw);

        // presenting the already-rotated token again = theft
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(raw));

        // the stolen-token response nuked the whole lineage — even the good successor is dead
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(rotated.rawToken()));
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate("never-issued-token"));
    }

    @Test
    void expiredTokenIsRejected() {
        String raw = store.issue(user);

        // a store whose clock is past the 7-day expiry sees the token as expired
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        RedisRefreshTokenStore later = storeAt(template, NOW.plus(Duration.ofDays(8)));

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> later.rotate(raw));
    }

    @Test
    void logoutRevokesTheToken() {
        String raw = store.issue(user);

        store.revoke(raw);   // idempotent + silent
        store.revoke(raw);

        // a revoked token presented to refresh is treated as reuse → rejected
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(raw));
    }

    @Test
    void revokeAllForUserKillsEveryActiveSession() {
        String one = store.issue(user);
        String two = store.issue(user);   // a second, independent session

        assertThat(store.revokeAllForUser(user)).isEqualTo(2);

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(one));
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> store.rotate(two));
        // idempotent: nothing left to revoke
        assertThat(store.revokeAllForUser(user)).isZero();
    }

    @Test
    void purgeIsANoOpBecauseRedisExpiresTokensItself() {
        assertThat(store.purgeDeadTokensOlderThan(NOW)).isZero();
    }
}
