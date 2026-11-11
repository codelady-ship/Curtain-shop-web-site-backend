package az.example.properde.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory admin login throttle: at most {@link #MAX_FAILURES} failed logins per
 * (client IP + username) within {@link #WINDOW}. Single instance only (state is not shared
 * and is lost on restart), which matches the single free Render instance.
 */
@Component
public class LoginRateLimiter {
    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public static String key(String clientIp, String username) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return String.valueOf(clientIp) + "|" + user;
    }

    public boolean isBlocked(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            prune(attempts);
            return attempts.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String key) {
        if (failures.size() >= MAX_KEYS) {
            failures.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    prune(entry.getValue());
                    return entry.getValue().isEmpty();
                }
            });
        }
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
    }
}
