package com.example.student.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    // TTL in seconds per cache name. Every namespace here gets a Caffeine (L1) cache;
    // a namespace NOT listed would silently skip L1 and be Redis-only, so keep this in
    // sync with every cacheService.get(...) call site.
    // Map.ofEntries (not Map.of) because there are more than 10 namespaces.
    static final Map<String, Long> TTLS = Map.ofEntries(
        Map.entry("subjects",     86400L),   // 24 h
        Map.entry("concepts",     86400L),   // 24 h
        Map.entry("roadmaps",     86400L),   // 24 h
        Map.entry("missions",     86400L),   // 24 h — reference data, rarely changes
        Map.entry("problems",     86400L),   // 24 h — reference data, rarely changes
        Map.entry("codingProblems",86400L),   // 24 h — LeetCode-style problems, seeded/rarely changes
        Map.entry("aptitude",     86400L),   // 24 h — reference data, rarely changes
        Map.entry("progress",     300L),     // 5 min — per-user, evicted on quiz pass / completion
        Map.entry("certificates", 300L),     // 5 min — per-user, evicted when a certificate is issued
        Map.entry("publicStats",  300L),     // 5 min — landing-page counts, cheap-to-spam public endpoint
        Map.entry("adminStats",   90L),      // 90 s — admin dashboard aggregate counts (heavy to compute)
        Map.entry("hunterStats",  60L),      // 60 s — per-user badges/counts, evicted on quiz pass / uncomplete
        Map.entry("publicProfile",90L)       // 90 s — public hunter profile, evicted on self-update
    );

    private final Map<String, Cache<String, Object>> caffeineCaches;
    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(
            @Autowired(required = false) @Qualifier("cacheRedisTemplate")
            RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.caffeineCaches = new HashMap<>();
        TTLS.forEach((name, ttl) ->
            caffeineCaches.put(name, Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(ttl, TimeUnit.SECONDS)
                .build())
        );
    }

    /**
     * Get a value: Caffeine → Redis → dbSupplier, warming upper layers on miss.
     * Never throws even if Redis is down.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Supplier<T> dbSupplier) {
        Cache<String, Object> caffeine = caffeineCaches.get(cacheName);
        String redisKey = cacheName + ":" + key;

        // L1 — Caffeine (microseconds)
        if (caffeine != null) {
            Object hit = caffeine.getIfPresent(key);
            if (hit != null) return (T) hit;
        }

        // L2 — Redis (2-5 ms, survives restarts)
        if (redisTemplate != null) {
            try {
                Object hit = redisTemplate.opsForValue().get(redisKey);
                if (hit != null) {
                    if (caffeine != null) caffeine.put(key, hit); // warm L1
                    return (T) hit;
                }
            } catch (Exception e) {
                log.warn("Redis GET failed [{}]: {}", redisKey, e.getMessage());
            }
        }

        // L3 — Database
        T value = dbSupplier.get();
        if (value != null) {
            if (caffeine != null) caffeine.put(key, value);
            if (redisTemplate != null) {
                try {
                    long ttl = TTLS.getOrDefault(cacheName, 3600L);
                    redisTemplate.opsForValue().set(redisKey, value, Duration.ofSeconds(ttl));
                } catch (Exception e) {
                    log.warn("Redis SET failed [{}]: {}", redisKey, e.getMessage());
                }
            }
        }
        return value;
    }

    /**
     * Numeric-safe variant of {@link #get}. The Redis JSON serializer does not tag type
     * info for final classes (Long is final), so a cached count round-trips back as an
     * Integer. Reading it straight into a {@code long} would insert a checked cast to
     * Long and blow up with a ClassCastException the moment the value comes from Redis
     * (L2) rather than Caffeine (L1). Reading it as a {@link Number} tolerates either box
     * type, so this is safe for both freshly-written and previously-cached values.
     */
    public long getLong(String cacheName, String key, Supplier<Long> dbSupplier) {
        // Force T=Object so the call site never emits a checkcast to Long.
        Supplier<Object> objSupplier = dbSupplier::get;
        Object hit = get(cacheName, key, objSupplier);
        return hit instanceof Number ? ((Number) hit).longValue() : 0L;
    }

    /** Evict a single key from both cache levels. */
    public void evict(String cacheName, String key) {
        Cache<String, Object> caffeine = caffeineCaches.get(cacheName);
        if (caffeine != null) caffeine.invalidate(key);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(cacheName + ":" + key);
            } catch (Exception e) {
                log.warn("Redis DELETE failed [{}:{}]: {}", cacheName, key, e.getMessage());
            }
        }
    }

    /** Evict every key in a cache namespace from both levels. */
    public void evictAll(String cacheName) {
        Cache<String, Object> caffeine = caffeineCaches.get(cacheName);
        if (caffeine != null) caffeine.invalidateAll();
        if (redisTemplate != null) {
            try {
                // SCAN instead of KEYS: iterate the keyspace in small batches so Redis is
                // never blocked. Same match pattern → the exact same keys get deleted.
                ScanOptions options = ScanOptions.scanOptions().match(cacheName + ":*").count(200).build();
                List<String> batch = new ArrayList<>();
                try (Cursor<String> cursor = redisTemplate.scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= 500) {
                            redisTemplate.delete(batch);
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) redisTemplate.delete(batch);
            } catch (Exception e) {
                log.warn("Redis SCAN/DELETE failed [{}:*]: {}", cacheName, e.getMessage());
            }
        }
    }
}
