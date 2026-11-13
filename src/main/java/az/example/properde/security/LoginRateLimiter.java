package az.example.properde.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory admin login throttle (single instance; state is lost on restart):
 * <ul>
 *   <li>at most {@link #MAX_FAILURES_PER_IP_AND_USER} failed logins per (client IP + username), and</li>
 *   <li>at most {@link #MAX_FAILURES_PER_USER} failed logins per username across all IPs,</li>
 * </ul>
 * within {@link #WINDOW}. {@link #tryAcquire} checks and records in one atomic step: every attempt
 * is counted as a failure up front, and {@link #onSuccess} removes it again, so parallel requests
 * cannot all slip past the check before any failure is recorded.
 */
@Component
public class LoginRateLimiter {
    public static final int MAX_FAILURES_PER_IP_AND_USER = 5;
    public static final int MAX_FAILURES_PER_USER = 20;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Instant>> byIpAndUser = new HashMap<>();
    private final Map<String, Deque<Instant>> byUser = new HashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns false (blocked) without recording anything, or true after recording this attempt. */
    public synchronized boolean tryAcquire(String clientIp, String username) {
        Instant now = clock.instant();
        String user = normalizeUser(username);
        String ipUser = String.valueOf(clientIp) + "|" + user;
        Deque<Instant> pair = prune(byIpAndUser.get(ipUser), now);
        Deque<Instant> perUser = prune(byUser.get(user), now);
        if ((pair != null && pair.size() >= MAX_FAILURES_PER_IP_AND_USER)
                || (perUser != null && perUser.size() >= MAX_FAILURES_PER_USER)) {
            return false;
        }
        if (byIpAndUser.size() >= MAX_KEYS || byUser.size() >= MAX_KEYS) {
            evictExpired(now);
        }
        byIpAndUser.computeIfAbsent(ipUser, k -> new ArrayDeque<>()).addLast(now);
        byUser.computeIfAbsent(user, k -> new ArrayDeque<>()).addLast(now);
        return true;
    }

    /** Successful login: clear this IP+username and take back the attempt counted for the username. */
    public synchronized void onSuccess(String clientIp, String username) {
        String user = normalizeUser(username);
        byIpAndUser.remove(String.valueOf(clientIp) + "|" + user);
        Deque<Instant> perUser = byUser.get(user);
        if (perUser != null) {
            perUser.pollLast();
            if (perUser.isEmpty()) {
                byUser.remove(user);
            }
        }
    }

    private static String normalizeUser(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private Deque<Instant> prune(Deque<Instant> attempts, Instant now) {
        if (attempts == null) {
            return null;
        }
        Instant cutoff = now.minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
        return attempts;
    }

    private void evictExpired(Instant now) {
        byIpAndUser.values().removeIf(d -> prune(d, now).isEmpty());
        byUser.values().removeIf(d -> prune(d, now).isEmpty());
    }
}
