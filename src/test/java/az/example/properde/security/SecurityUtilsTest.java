package az.example.properde.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {
    private static final String SECRET = "unit-test-secret-0123456789-abcdefghij";

    @Test
    void jwtSecretIsRequiredAndAtLeast32Bytes() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenService("", 3600));
        assertThrows(IllegalStateException.class, () -> new JwtTokenService(null, 3600));
        assertThrows(IllegalStateException.class, () -> new JwtTokenService("only-31-bytes-long-secret-12345", 3600));
        assertDoesNotThrow(() -> new JwtTokenService("exactly-32-bytes-long-secret-123", 3600));
    }

    @Test
    void jwtRejectsTamperedAndExpiredTokens() {
        JwtTokenService jwt = new JwtTokenService(SECRET, 3600);
        String token = jwt.issue(7L, "admin");
        assertTrue(jwt.isValid(token));
        String[] parts = token.split("\\.");
        String flipped = parts[2].endsWith("A") ? "B" : "A";
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 1) + flipped;
        assertFalse(jwt.isValid(tampered));
        assertFalse(jwt.isValid(new JwtTokenService(SECRET, -10).issue(7L, "admin")));
        assertFalse(jwt.isValid(null));
    }

    @Test
    void bcryptHashesAndLegacyHashesBothVerify() {
        String hash = PasswordHashUtil.hash("correct horse battery");
        assertTrue(hash.startsWith("$2"));
        assertFalse(PasswordHashUtil.needsRehash(hash));
        assertTrue(PasswordHashUtil.matches("correct horse battery", PasswordHashUtil.BCRYPT_SALT_MARKER, hash));
        assertFalse(PasswordHashUtil.matches("wrong", PasswordHashUtil.BCRYPT_SALT_MARKER, hash));

        String legacy = PasswordHashUtil.legacySha256("old-password", "salt1");
        assertTrue(PasswordHashUtil.needsRehash(legacy));
        assertTrue(PasswordHashUtil.matches("old-password", "salt1", legacy));
        assertFalse(PasswordHashUtil.matches("old-password", "other", legacy));
    }

    @Test
    void loginRateLimiterBlocksAfterFiveFailuresForFifteenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-11T10:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);

        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP_AND_USER; i++) {
            assertTrue(limiter.tryAcquire("203.0.113.5", "Admin"));
        }
        assertFalse(limiter.tryAcquire("203.0.113.5", "Admin"));
        assertFalse(limiter.tryAcquire("203.0.113.5", " admin "));
        assertTrue(limiter.tryAcquire("203.0.113.6", "admin")); // other IP still allowed

        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertTrue(limiter.tryAcquire("203.0.113.5", "admin"));
        limiter.onSuccess("203.0.113.5", "admin");
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP_AND_USER; i++) {
            assertTrue(limiter.tryAcquire("203.0.113.5", "admin"));
        }
    }

    @Test
    void loginRateLimiterCapsOneUsernameAcrossManyIps() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-11T10:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER; i++) {
            assertTrue(limiter.tryAcquire("198.51.100." + i, "admin"), "attempt " + i);
        }
        assertFalse(limiter.tryAcquire("198.51.100.250", "admin"));
        assertTrue(limiter.tryAcquire("198.51.100.250", "someone-else"));
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertTrue(limiter.tryAcquire("198.51.100.250", "admin"));
    }

    @Test
    void loginRateLimiterCheckAndRecordIsAtomic() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                start.await();
                if (limiter.tryAcquire("192.0.2.1", "admin")) {
                    allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(LoginRateLimiter.MAX_FAILURES_PER_IP_AND_USER, allowed.get());
    }

    @Test
    void passwordsOver72BytesNeverMatchBcrypt() {
        String hash = PasswordHashUtil.hash("a".repeat(72));
        assertTrue(PasswordHashUtil.matches("a".repeat(72), PasswordHashUtil.BCRYPT_SALT_MARKER, hash));
        assertFalse(PasswordHashUtil.matches("a".repeat(73), PasswordHashUtil.BCRYPT_SALT_MARKER, hash));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
