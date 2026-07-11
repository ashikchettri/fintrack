package com.fintrack.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LoginAttemptServiceTest {

    /** Steppable clock — window expiry is tested by moving time, not sleeping. */
    private static final class SteppableClock extends Clock {
        private Instant now = Instant.parse("2026-07-12T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private SteppableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new SteppableClock();
        service = new LoginAttemptService(clock);
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(email);
        }
    }

    @Test
    void underTheLimitIsNotThrottled() {
        fail("jane@example.com", LoginAttemptService.MAX_FAILURES - 1);

        assertThatCode(() -> service.checkNotThrottled("jane@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void hittingTheLimitThrottles() {
        fail("jane@example.com", LoginAttemptService.MAX_FAILURES);

        assertThatExceptionOfType(TooManyLoginAttemptsException.class)
                .isThrownBy(() -> service.checkNotThrottled("jane@example.com"));
    }

    @Test
    void throttleIsPerEmail() {
        fail("attacker-target@example.com", LoginAttemptService.MAX_FAILURES);

        assertThatCode(() -> service.checkNotThrottled("someone-else@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void windowExpiryLiftsTheThrottle() {
        fail("jane@example.com", LoginAttemptService.MAX_FAILURES);
        clock.advance(LoginAttemptService.WINDOW.plusSeconds(1));

        assertThatCode(() -> service.checkNotThrottled("jane@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounter() {
        fail("jane@example.com", LoginAttemptService.MAX_FAILURES - 1);
        service.recordSuccess("jane@example.com");
        fail("jane@example.com", 1);

        assertThatCode(() -> service.checkNotThrottled("jane@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void failuresAfterAnExpiredWindowStartANewWindow() {
        fail("jane@example.com", LoginAttemptService.MAX_FAILURES);
        clock.advance(LoginAttemptService.WINDOW.plusSeconds(1));
        fail("jane@example.com", 1);

        // only 1 failure in the new window — not throttled
        assertThatCode(() -> service.checkNotThrottled("jane@example.com"))
                .doesNotThrowAnyException();
    }
}
