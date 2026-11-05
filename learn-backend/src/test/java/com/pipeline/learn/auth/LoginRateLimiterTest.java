package com.pipeline.learn.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.pipeline.learn.common.ApiException;

class LoginRateLimiterTest {

    /** A clock the test can move forward. */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-05T12:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void pairLocksAfterEightFailuresAndOpensAfterTenMinutes() {
        MovableClock clock = new MovableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 8; i++) {
            limiter.checkAllowed("1.1.1.1", "anna");
            limiter.recordFailure("1.1.1.1", "anna");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("1.1.1.1", "anna")).isInstanceOf(ApiException.class);

        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "anna")).doesNotThrowAnyException();
    }

    @Test
    void oneAddressFumblingDoesNotLockTheSameLoginForEveryoneElse() {
        LoginRateLimiter limiter = new LoginRateLimiter(new MovableClock());
        for (int i = 0; i < 8; i++) {
            limiter.recordFailure("1.1.1.1", "anna");
        }
        // A stranger's 8 failures lock only that address+login pair, not Anna's own visits from home.
        assertThatCode(() -> limiter.checkAllowed("2.2.2.2", "anna")).doesNotThrowAnyException();
    }

    @Test
    void guessingSpreadOverManyAddressesStillLocksTheLogin() {
        LoginRateLimiter limiter = new LoginRateLimiter(new MovableClock());
        for (int i = 0; i < 40; i++) {
            limiter.recordFailure("10.0.0." + i, "anna");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("9.9.9.9", "anna")).isInstanceOf(ApiException.class);
    }

    @Test
    void successForgetsThePairFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(new MovableClock());
        for (int i = 0; i < 7; i++) {
            limiter.recordFailure("1.1.1.1", "anna");
        }
        limiter.recordSuccess("1.1.1.1", "anna");
        limiter.recordFailure("1.1.1.1", "anna");
        assertThat(true).isTrue();
        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "anna")).doesNotThrowAnyException();
    }
}
