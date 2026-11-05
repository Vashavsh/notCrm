package com.pipeline.learn.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pipeline.learn.common.ApiException;

import org.springframework.http.HttpStatus;

/**
 * In-memory brute-force guard for login. Three independent limits, each counting failures in a
 * window and locking its key for a while once exceeded:
 * <ul>
 *   <li>per (address, login) pair: tight, so one person fumbling a password locks only themselves;</li>
 *   <li>per address: catches a script walking through many logins;</li>
 *   <li>per login: loose, catches guessing spread over many addresses. It is deliberately much looser than
 *       the pair limit, so a stranger cannot lock a known login out of the site with a handful of requests.</li>
 * </ul>
 * State is per process and resets on restart, which is fine for a single instance.
 */
@Component
public class LoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCKOUT = Duration.ofMinutes(10);
    private static final int PAIR_MAX = 8;
    private static final int ADDRESS_MAX = 60;
    private static final int LOGIN_MAX = 40;

    private record Bucket(int count, Instant windowStart, Instant lockedUntil) {
        static final Bucket EMPTY = new Bucket(0, Instant.EPOCH, Instant.EPOCH);
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String address, String login) {
        for (String key : keys(address, login)) {
            Bucket b = buckets.getOrDefault(key, Bucket.EMPTY);
            if (clock.instant().isBefore(b.lockedUntil())) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток. Повторите через 10 минут.");
            }
        }
    }

    public void recordFailure(String address, String login) {
        fail("pair:" + address + "|" + login, PAIR_MAX);
        fail("ip:" + address, ADDRESS_MAX);
        fail("login:" + login, LOGIN_MAX);
    }

    /** For limits that are not about login: fails if {@code key} is locked. */
    public void checkAllowedKey(String key) {
        Bucket b = buckets.getOrDefault(key, Bucket.EMPTY);
        if (clock.instant().isBefore(b.lockedUntil())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток. Повторите через 10 минут.");
        }
    }

    public void recordFailureKey(String key, int max) {
        fail(key, max);
    }

    public void recordSuccess(String address, String login) {
        buckets.remove("pair:" + address + "|" + login);
    }

    private String[] keys(String address, String login) {
        return new String[] {"pair:" + address + "|" + login, "ip:" + address, "login:" + login};
    }

    private void fail(String key, int max) {
        buckets.compute(key, (k, existing) -> {
            Instant now = clock.instant();
            Bucket b = existing == null ? Bucket.EMPTY : existing;
            boolean expired = Duration.between(b.windowStart(), now).compareTo(WINDOW) > 0;
            int count = (expired ? 0 : b.count()) + 1;
            Instant windowStart = expired ? now : b.windowStart();
            Instant lockedUntil = count >= max ? now.plus(LOCKOUT) : b.lockedUntil();
            return new Bucket(count, windowStart, lockedUntil);
        });
    }
}
