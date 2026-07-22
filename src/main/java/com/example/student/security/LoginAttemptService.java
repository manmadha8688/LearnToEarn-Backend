package com.example.student.security;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login throttling to slow credential-stuffing / password-spraying.
 * Keyed by (email + client IP). After MAX_ATTEMPTS consecutive failures the key is
 * locked for LOCK_MINUTES. A successful login clears the counter.
 *
 * Kept in-memory (single instance) to mirror {@code OtpService}; no new infra required.
 */
@Service
@Lazy(false) // @Scheduled cleanup must be eager so its task registers under lazy-init
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private record Attempt(int count, LocalDateTime firstAt, LocalDateTime lockedUntil) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private String key(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    /** @return remaining lock seconds if blocked, otherwise 0. */
    public long blockedForSeconds(String email, String ip) {
        Attempt a = attempts.get(key(email, ip));
        if (a == null || a.lockedUntil() == null) return 0;
        long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(IST), a.lockedUntil());
        return Math.max(secs, 0);
    }

    public void recordFailure(String email, String ip) {
        String k = key(email, ip);
        LocalDateTime now = LocalDateTime.now(IST);
        attempts.compute(k, (key, existing) -> {
            if (existing == null || existing.lockedUntil() != null && existing.lockedUntil().isBefore(now)) {
                return new Attempt(1, now, null);
            }
            int next = existing.count() + 1;
            LocalDateTime lockedUntil = next >= MAX_ATTEMPTS ? now.plusMinutes(LOCK_MINUTES) : null;
            return new Attempt(next, existing.firstAt(), lockedUntil);
        });
    }

    public void reset(String email, String ip) {
        attempts.remove(key(email, ip));
    }

    // Clear stale entries hourly to keep the map bounded.
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(IST);
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            if (a.lockedUntil() != null) return a.lockedUntil().isBefore(now);
            return ChronoUnit.HOURS.between(a.firstAt(), now) >= 1;
        });
    }
}
