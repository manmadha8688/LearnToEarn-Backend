package com.example.student.security;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic fixed-window rate limiter for unauthenticated / cheap-to-spam endpoints
 * (guest creation, registration, feedback). Keyed by (action + client IP).
 *
 * Kept in-memory to mirror {@link LoginAttemptService} and {@code OtpService} — no
 * new infrastructure. For a multi-instance deployment this should move to Redis;
 * see the enterprise-scalability notes. On a single Render instance this is exact.
 */
@Service
@Lazy(false) // @Scheduled cleanup must be eager so its task registers under lazy-init
public class RateLimiterService {

    private record Window(int count, Instant resetAt) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private String key(String action, String ip) {
        return (action == null ? "" : action) + "|" + (ip == null ? "" : ip);
    }

    /**
     * Records one hit and reports whether the caller is now over the limit.
     *
     * @return remaining seconds until the window resets if the limit is exceeded, else 0.
     */
    public long hit(String action, String ip, int maxPerWindow, long windowSeconds) {
        String k = key(action, ip);
        Instant now = Instant.now();
        Window w = windows.compute(k, (key, existing) -> {
            if (existing == null || existing.resetAt().isBefore(now)) {
                return new Window(1, now.plusSeconds(windowSeconds));
            }
            return new Window(existing.count() + 1, existing.resetAt());
        });
        if (w.count() > maxPerWindow) {
            long secs = now.until(w.resetAt(), java.time.temporal.ChronoUnit.SECONDS);
            return Math.max(secs, 1);
        }
        return 0;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanup() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(e -> e.getValue().resetAt().isBefore(now));
    }
}
