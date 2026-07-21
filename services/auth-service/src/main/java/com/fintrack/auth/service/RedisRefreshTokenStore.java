package com.fintrack.auth.service;

import com.fintrack.auth.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed refresh-token store (ADR 011), active when
 * {@code fintrack.auth.refresh-token.store=redis}. Reproduces the Postgres
 * store's behavior — rotation chain, single-use, reuse detection — on Redis,
 * with native TTL doing the housekeeping the daily purge job did.
 *
 * <p>Data model: {@code rt:{hash}} is a hash {@code {user, family, state, exp}}
 * ({@code state} = active|used|revoked); {@code rtu:{user}} is the set of the
 * user's session families; {@code rtf:{family}} is the set of a lineage's token
 * hashes. Every key carries a TTL of the audit window, so a replayed token is
 * still present (and detectable as reuse) as long as a Postgres dead row would
 * be kept. Rotation, revoke, and revoke-all run as Lua scripts — Redis executes
 * each atomically, so validate→consume→store-successor and the reuse cascade
 * can't interleave. Assumes a single Redis / primary (the scripts touch
 * non-co-located keys); Redis Cluster would hash-tag by user.
 */
@Component
@ConditionalOnProperty(prefix = "fintrack.auth.refresh-token", name = "store", havingValue = "redis")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenStore.class);

    /** Dead tokens stay this long for reuse detection + incident audit (matches the JPA store). */
    static final Duration AUDIT_WINDOW = Duration.ofDays(30);

    private final StringRedisTemplate redis;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisRefreshTokenStore(StringRedisTemplate redis, JwtProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String issue(UUID userId) {
        String rawToken = newRawToken();
        String family = UUID.randomUUID().toString();
        long exp = clock.instant().plus(properties.refreshTokenTtl()).toEpochMilli();

        redis.execute(ISSUE, List.of(),
                TokenService.sha256Hex(rawToken), userId.toString(), family,
                Long.toString(exp), ttlSeconds());
        return rawToken;
    }

    @Override
    public Rotation rotate(String presentedRawToken) {
        String successorRaw = newRawToken();
        long successorExp = clock.instant().plus(properties.refreshTokenTtl()).toEpochMilli();

        String result = redis.execute(ROTATE, List.of(),
                TokenService.sha256Hex(presentedRawToken), TokenService.sha256Hex(successorRaw),
                Long.toString(successorExp), ttlSeconds(), Long.toString(clock.millis()));

        if (result != null && result.startsWith("ok:")) {
            return new Rotation(successorRaw, UUID.fromString(result.substring(3)));
        }
        if (result != null && result.startsWith("reuse:")) {
            log.warn("Refresh token reuse detected for user {} — all sessions revoked", result.substring(6));
        }
        throw new InvalidRefreshTokenException();
    }

    @Override
    public void revoke(String presentedRawToken) {
        redis.execute(REVOKE, List.of(), TokenService.sha256Hex(presentedRawToken));
    }

    @Override
    public int revokeAllForUser(UUID userId) {
        Long revoked = redis.execute(REVOKE_ALL, List.of(), userId.toString());
        return revoked == null ? 0 : revoked.intValue();
    }

    @Override
    public int purgeDeadTokensOlderThan(Instant cutoff) {
        return 0;   // Redis TTL retires dead tokens; no sweep needed
    }

    private String ttlSeconds() {
        return Long.toString(AUDIT_WINDOW.toSeconds());
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ---- Lua (each runs atomically on the Redis server) --------------------

    // ARGV: hash, user, family, exp, ttl
    private static final RedisScript<String> ISSUE = RedisScript.of("""
            redis.call('HSET', 'rt:'..ARGV[1], 'user', ARGV[2], 'family', ARGV[3], 'state', 'active', 'exp', ARGV[4])
            redis.call('EXPIRE', 'rt:'..ARGV[1], ARGV[5])
            redis.call('SADD', 'rtu:'..ARGV[2], ARGV[3])
            redis.call('EXPIRE', 'rtu:'..ARGV[2], ARGV[5])
            redis.call('SADD', 'rtf:'..ARGV[3], ARGV[1])
            redis.call('EXPIRE', 'rtf:'..ARGV[3], ARGV[5])
            return 'ok'
            """, String.class);

    // ARGV: presentedHash, successorHash, successorExp, ttl, now
    private static final RedisScript<String> ROTATE = RedisScript.of("""
            local key = 'rt:'..ARGV[1]
            if redis.call('EXISTS', key) == 0 then return 'invalid' end
            local state = redis.call('HGET', key, 'state')
            local user = redis.call('HGET', key, 'user')
            local family = redis.call('HGET', key, 'family')
            local exp = redis.call('HGET', key, 'exp')
            if state ~= 'active' then
              local fams = redis.call('SMEMBERS', 'rtu:'..user)
              for _, fam in ipairs(fams) do
                local hs = redis.call('SMEMBERS', 'rtf:'..fam)
                for _, h in ipairs(hs) do
                  redis.call('HSET', 'rt:'..h, 'state', 'revoked')
                end
              end
              return 'reuse:'..user
            end
            if tonumber(ARGV[5]) > tonumber(exp) then return 'expired' end
            redis.call('HSET', key, 'state', 'used')
            redis.call('HSET', 'rt:'..ARGV[2], 'user', user, 'family', family, 'state', 'active', 'exp', ARGV[3])
            redis.call('EXPIRE', 'rt:'..ARGV[2], ARGV[4])
            redis.call('SADD', 'rtf:'..family, ARGV[2])
            redis.call('EXPIRE', 'rtf:'..family, ARGV[4])
            redis.call('EXPIRE', 'rtu:'..user, ARGV[4])
            return 'ok:'..user
            """, String.class);

    // ARGV: hash
    private static final RedisScript<String> REVOKE = RedisScript.of("""
            local key = 'rt:'..ARGV[1]
            if redis.call('EXISTS', key) == 1 and redis.call('HGET', key, 'state') == 'active' then
              redis.call('HSET', key, 'state', 'revoked')
            end
            return 'ok'
            """, String.class);

    // ARGV: user
    private static final RedisScript<Long> REVOKE_ALL = RedisScript.of("""
            local count = 0
            local fams = redis.call('SMEMBERS', 'rtu:'..ARGV[1])
            for _, fam in ipairs(fams) do
              local hs = redis.call('SMEMBERS', 'rtf:'..fam)
              for _, h in ipairs(hs) do
                if redis.call('HGET', 'rt:'..h, 'state') == 'active' then
                  redis.call('HSET', 'rt:'..h, 'state', 'revoked')
                  count = count + 1
                end
              end
            end
            return count
            """, Long.class);
}
