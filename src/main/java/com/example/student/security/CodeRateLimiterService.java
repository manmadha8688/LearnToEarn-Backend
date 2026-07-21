package com.example.student.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-user, per-endpoint rate limiter for the code-execution endpoints
 * (/api/code/execute → "run", /api/code/judge → "submit").
 *
 * <p>Backed by Redis in prod ({@code spring.cache.type=redis}) so the limit holds
 * across every Render instance: a counter keyed
 * {@code rate_limit:{userId}:{endpoint}:{minuteWindow}} is INCR-ed per minute
 * window and given a 60s TTL so it auto-expires. If Redis is not configured
 * (local dev) or is momentarily unreachable, it transparently falls back to an
 * in-memory fixed window — never blocking a run because the cache is down.
 *
 * <p>Only the code endpoints use this; all other endpoints keep using
 * {@link RateLimiterService} unchanged.
 */
@Service
public class CodeRateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(CodeRateLimiterService.class);
    private static final long WINDOW_SECONDS = 60;

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final boolean redisEnabled;

    // In-memory fallback (single instance / local / Redis-down).
    private record Window(int count, Instant resetAt) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public CodeRateLimiterService(ObjectProvider<StringRedisTemplate> redisProvider,
                                  @Value("${spring.cache.type:}") String cacheType) {
        this.redisProvider = redisProvider;
        this.redisEnabled = "redis".equalsIgnoreCase(cacheType);
    }

    /**
     * Records one hit for {@code userId} on {@code endpoint} in the current minute
     * window and reports whether the caller is now over {@code maxPerWindow}.
     *
     * @return seconds until the window resets if the limit is exceeded, else 0.
     */
    public long hit(String userId, String endpoint, int maxPerWindow) {
        long minuteWindow = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        String key = "rate_limit:" + (userId == null ? "anon" : userId) + ":" + endpoint + ":" + minuteWindow;

        if (redisEnabled) {
            Long retry = hitRedis(key, maxPerWindow);
            if (retry != null) return retry;      // null → Redis failed, fall through to memory
        }
        return hitMemory(key, maxPerWindow);
    }

    /** Returns retry-after seconds (>=0) on success, or null if Redis was unavailable. */
    private Long hitRedis(String key, int maxPerWindow) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) return null;
            Long count = redis.opsForValue().increment(key);
            if (count == null) return null;
            if (count == 1L) {
                // First hit in this window — set the TTL so the counter self-expires.
                redis.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count > maxPerWindow) {
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                return (ttl != null && ttl > 0) ? ttl : WINDOW_SECONDS;
            }
            return 0L;
        } catch (Exception e) {
            log.warn("Redis rate-limit check failed for {} — falling back to in-memory: {}", key, e.getMessage());
            return null;
        }
    }

    private long hitMemory(String key, int maxPerWindow) {
        Instant now = Instant.now();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.resetAt().isBefore(now)) {
                return new Window(1, now.plusSeconds(WINDOW_SECONDS));
            }
            return new Window(existing.count() + 1, existing.resetAt());
        });
        if (w.count() > maxPerWindow) {
            long secs = now.until(w.resetAt(), ChronoUnit.SECONDS);
            return Math.max(secs, 1);
        }
        return 0;
    }

    /** Drops expired in-memory windows so the fallback map can't grow unbounded. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanup() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(e -> e.getValue().resetAt().isBefore(now));
    }
}
