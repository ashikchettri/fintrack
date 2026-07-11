package com.fintrack.auth.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force throttle: 5 failures per email in a 15-minute window → 429.
 *
 * Keyed on the *submitted* email whether or not an account exists, so the
 * throttle itself can't be used to enumerate accounts.
 *
 * Deliberately in-memory (per-instance, resets on restart): the durable,
 * cluster-wide version moves to Redis at the gateway in phase 2. Good enough
 * against online guessing meanwhile — Argon2id already makes each attempt
 * expensive.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant windowStart) {
    }

    private final ConcurrentHashMap<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Called before verifying credentials. */
    public void checkNotThrottled(String email) {
        Attempts attempts = attemptsByEmail.get(email);
        if (attempts == null) {
            return;
        }
        if (windowExpired(attempts)) {
            attemptsByEmail.remove(email, attempts);
            return;
        }
        if (attempts.failures() >= MAX_FAILURES) {
            throw new TooManyLoginAttemptsException();
        }
    }

    public void recordFailure(String email) {
        attemptsByEmail.compute(email, (k, current) ->
                current == null || windowExpired(current)
                        ? new Attempts(1, clock.instant())
                        : new Attempts(current.failures() + 1, current.windowStart()));
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(email);
    }

    private boolean windowExpired(Attempts attempts) {
        return clock.instant().isAfter(attempts.windowStart().plus(WINDOW));
    }
}
